package com.ai.recruitmentai.service;
import com.ai.recruitmentai.config.AsyncConfig;
import com.ai.recruitmentai.dto.CvDataDto;
import com.ai.recruitmentai.entity.Candidate;
import com.ai.recruitmentai.exception.FileParsingException;
import com.ai.recruitmentai.exception.FileStorageException;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.llm.OllamaClient;
import com.ai.recruitmentai.llm.PromptFactory;
import com.ai.recruitmentai.repository.CandidateRepository;
import com.ai.recruitmentai.util.FileStorageService;
import com.ai.recruitmentai.util.PdfParserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
public class CandidateService {
    private static final Logger log=LoggerFactory.getLogger(CandidateService.class);
    private static final Pattern FILENAME_ID_PATTERN=Pattern.compile("^(C\\d+)\\..*", Pattern.CASE_INSENSITIVE);
    private final CandidateRepository candidateRepository;
    private final FileStorageService fileStorageService;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    @Autowired
    public CandidateService(CandidateRepository candidateRepository,
                            FileStorageService fileStorageService,
                            OllamaClient ollamaClient,
                            ObjectMapper objectMapper) {
        this.candidateRepository=candidateRepository;
        this.fileStorageService=fileStorageService;
        this.ollamaClient=ollamaClient;
        this.objectMapper=objectMapper;
    }
    @Transactional
    public Candidate storeAndInitiateParsing(MultipartFile file) {
        log.info("Storing and initiating parsing for file: {}", file.getOriginalFilename());
        Path filePath=fileStorageService.storeFile(file);
        String storedFilePathString=filePath.toString();
        String originalFilename=filePath.getFileName().toString();
        String candidateIdFromFile=extractCandidateIdFromFilename(originalFilename);
        if (candidateIdFromFile == null) {
            throw new FileStorageException("Could not extract required Candidate ID (e.g., Cxxxx) from filename: " + originalFilename);
        }
        log.info("Extracted Candidate ID '{}' from filename '{}'", candidateIdFromFile, originalFilename);
        Candidate candidate=candidateRepository.findByCandidateIdFromFile(candidateIdFromFile)
                .map(existingCandidate -> {
                    log.warn("Candidate with File ID '{}' already exists. Updating file path ({}) and resetting status for re-parsing.", candidateIdFromFile, storedFilePathString);
                    existingCandidate.setOriginalFilePath(storedFilePathString);
                    existingCandidate.setStatus("UPLOADED");
                    existingCandidate.setExtractedCvJson(null);
                    existingCandidate.setName(null);
                    existingCandidate.setEmail(null);
                    existingCandidate.setPhone(null);
                    return candidateRepository.save(existingCandidate);
                })
                .orElseGet(() -> {
                    log.info("Creating new Candidate record for File ID '{}'", candidateIdFromFile);
                    Candidate newCandidate=new Candidate();
                    newCandidate.setCandidateIdFromFile(candidateIdFromFile);
                    newCandidate.setOriginalFilePath(storedFilePathString);
                    newCandidate.setStatus("UPLOADED");
                    return candidateRepository.save(newCandidate);
                });
        parseCvAsynchronously(candidate.getId());
        log.info("Initiated asynchronous parsing for Candidate DB ID: {}", candidate.getId());
        return candidate;
    }
    @Async(AsyncConfig.CV_PARSING_EXECUTOR_BEAN_NAME)
    @Transactional
    public void parseCvAsynchronously(Long candidateId) {
        log.info("[Async] Starting CV parsing task for Candidate ID: {}", candidateId);
        Candidate candidate=null;
        String cleanedJson=null;
        String rawLlmResponse=null;
        try {
            candidate=candidateRepository.findById(candidateId)
                    .orElseThrow(() -> {
                        log.error("[Async] Candidate {} not found for async parsing.", candidateId);
                        return new IllegalStateException("Candidate " + candidateId + " not found during async processing.");
                    });

            if ("PARSED".equals(candidate.getStatus())) {
                log.warn("[Async] Candidate {} already parsed successfully. Skipping.", candidateId);
                return;
            }
            candidate.setStatus("PARSING");
            candidateRepository.saveAndFlush(candidate);
            File cvFile=new File(candidate.getOriginalFilePath());
            if (!cvFile.exists()) {
                throw new FileParsingException("CV file not found at path: " + candidate.getOriginalFilePath());
            }
            log.info("[Async] Parsing PDF file: {}", cvFile.getPath());
            String rawCvText=PdfParserUtil.extractText(cvFile);
            if (rawCvText == null || rawCvText.isBlank()) {
                throw new FileParsingException("Extracted empty text from PDF: " + cvFile.getName());
            }
            log.info("[Async] Extracted {} characters from PDF for Candidate ID: {}", rawCvText.length(), candidateId);
            String prompt=PromptFactory.createCvExtractionPrompt(rawCvText);
            log.info("[Async] Sending CV text to LLM for extraction...");
            rawLlmResponse=ollamaClient.generate(prompt);
            cleanedJson=cleanLlmJsonResponse(rawLlmResponse);
            CvDataDto cvData=parseAndValidateCvJson(cleanedJson, candidateId);
            candidate.setName(cvData.getName());
            candidate.setEmail(cvData.getEmail());
            candidate.setPhone(cvData.getPhone());
            candidate.setExtractedCvJson(cleanedJson);
            candidate.setStatus("PARSED");
            log.info("[Async] Successfully parsed Candidate ID: {}", candidateId);
            candidateRepository.save(candidate);
        } catch (Exception e) {
            log.error("[Async] Error during async parsing process for candidate {}: {}", candidateId, e.getMessage(), e);
            String errorData=Objects.requireNonNullElse(cleanedJson, Objects.requireNonNullElse(rawLlmResponse, "Error: " + e.getMessage()));
            updateCandidateStatusOnError(candidateId, "ERROR_PARSING", errorData);
        }
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void updateCandidateStatusOnError(Long candidateId, String status, String errorJsonOrMsg) {
        if (candidateId == null) {
            log.error("Cannot update status for null candidateId.");
            return;
        }
        try {
            candidateRepository.findById(candidateId).ifPresentOrElse(
                    candidate -> {
                        candidate.setStatus(status);
                        candidate.setExtractedCvJson(StringUtils.abbreviate(errorJsonOrMsg, 1000));
                        candidate.setName(null);
                        candidate.setEmail(null);
                        candidate.setPhone(null);
                        candidateRepository.save(candidate);
                        log.info("[Async Error] Set status to {} for Candidate ID {}", status, candidateId);
                    },
                    () -> { 
                        log.warn("[Async Error] Candidate with ID {} not found when trying to update status to {}.", candidateId, status);
                    }
            );
        } catch (Exception e) {
            log.error("[Async Error] Failed *again* while trying to update status to {} for Candidate ID {}: {}", status, candidateId, e.getMessage(), e);
        }
    }
    private String cleanLlmJsonResponse(String rawResponse) throws LlmInteractionException {
        if (rawResponse == null) throw new LlmInteractionException("LLM returned null response.");
        String cleaned=rawResponse.trim();
        if (cleaned.startsWith("```json")) cleaned=cleaned.substring(7);
        if (cleaned.endsWith("```")) cleaned=cleaned.substring(0, cleaned.length() - 3);
        cleaned=cleaned.trim();
        if (cleaned.isEmpty() || !cleaned.startsWith("{") || !cleaned.endsWith("}")) {
            log.warn("LLM response not JSON-like after cleaning. Raw: '{}'", rawResponse);
            throw new LlmInteractionException("LLM response empty/not JSON-like after cleaning. Cleaned: '" + cleaned + "'");
        }
        log.debug("Cleaned LLM response: {}", cleaned);
        return cleaned;
    }
    private CvDataDto parseAndValidateCvJson(String jsonString, Long id) throws JsonProcessingException {
        if (objectMapper == null) throw new IllegalStateException("ObjectMapper required but not configured.");
        try {
            CvDataDto cvData=objectMapper.readValue(jsonString, CvDataDto.class);
            log.debug("LLM CV JSON parsed and validated successfully for ID: {}", id);
            return cvData;
        } catch (JsonProcessingException jsonEx) {
            log.error("LLM CV response is not valid JSON or doesn't match DTO for ID {}. JSON: '{}'", id, jsonString, jsonEx);
            throw jsonEx;
        }
    }
    private String extractCandidateIdFromFilename(String filename) {
        if (filename == null) return null;
        Matcher matcher=FILENAME_ID_PATTERN.matcher(filename);
        if (matcher.matches()) {
            return matcher.group(1).toUpperCase();
        }
        log.warn("Could not extract expected ID pattern (Cxxxx.pdf) from filename: {}", filename);
        return null;
    }

    public Optional<Candidate> getCandidateById(Long id) {
        return candidateRepository.findById(id);
    }
    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAll();
    }
}