package com.ai.recruitmentai.service;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.FileParsingException;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.llm.OllamaClient;
import com.ai.recruitmentai.llm.PromptFactory;
import com.ai.recruitmentai.repository.JobDescriptionRepository;
import com.ai.recruitmentai.util.CsvParserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
@Service
public class JobService {
    private static final Logger log=LoggerFactory.getLogger(JobService.class);
    private final JobDescriptionRepository jobDescriptionRepository;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    @Autowired
    public JobService(JobDescriptionRepository jobDescriptionRepository,
                      OllamaClient ollamaClient,
                      ObjectMapper objectMapper) {
        this.jobDescriptionRepository=jobDescriptionRepository;
        this.ollamaClient=ollamaClient;
        this.objectMapper=objectMapper;
    }
    @Transactional
    public List<JobDescription> loadJobsFromCsv(InputStream csvInputStream) {
        log.info("Attempting to load job descriptions from CSV stream.");
        try {
            List<JobDescription> jobDescriptions=CsvParserUtil.parseJobDescriptions(csvInputStream);
            if (jobDescriptions.isEmpty()) {
                log.warn("No valid job descriptions found in the provided CSV stream.");
                return List.of();
            }
            List<JobDescription> savedJobs=jobDescriptionRepository.saveAll(jobDescriptions);
            log.info("Successfully saved {} new job descriptions from CSV.", savedJobs.size());
            return savedJobs;
        } catch (FileParsingException e) {
            log.error("Failed to parse CSV file.", e);
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred while saving job descriptions from CSV.", e);
            throw new RuntimeException("Failed to save job descriptions to database.", e);
        }
    }
    @Transactional
    public JobDescription summarizeJob(Long jobId) {
        log.info("Attempting to summarize job description with ID: {}", jobId);
        JobDescription job=jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobDescription not found with ID: " + jobId));
        if ("SUMMARIZED".equals(job.getStatus()) &&
                job.getStructuredSummaryJson() != null &&
                job.getStructuredSummaryJson().startsWith("{") &&
                job.getStructuredSummaryJson().endsWith("}"))
        {
            log.warn("Job description {} already has a valid summary. Skipping summarization.", jobId);
            return job;
        }

        if (job.getRawDescription() == null || job.getRawDescription().isBlank()) {
            log.error("Cannot summarize job description {}: Raw description text is missing.", jobId);
            job.setStatus("ERROR_SUMMARIZING");
            job.setStructuredSummaryJson(null);
            return jobDescriptionRepository.save(job);
        }

        String cleanedJson=null;
        try {
            String prompt=PromptFactory.createJdSummaryPrompt(job.getRawDescription());
            log.info("Sending JD summarization prompt to LLM for Job ID: {}", jobId);
            String rawLlmResponse=ollamaClient.generate(prompt);
            cleanedJson=cleanLlmJsonResponse(rawLlmResponse);
            validateJsonStructure(cleanedJson, jobId, "summary");
            log.debug("Setting structuredSummaryJson for Job ID {} to: {}", jobId, cleanedJson);
            job.setStructuredSummaryJson(cleanedJson);
            job.setStatus("SUMMARIZED");
            log.info("Successfully summarized job description ID: {}.", jobId);
        } catch (LlmInteractionException | JsonProcessingException e) {
            log.error("LLM interaction or JSON validation failed while summarizing job ID: {}", jobId, e);
            job.setStatus("ERROR_SUMMARIZING");
            job.setStructuredSummaryJson(Objects.requireNonNullElse(cleanedJson, "Error processing summary: " + e.getMessage()));
            jobDescriptionRepository.save(job);
            throw new LlmInteractionException("Failed to get or validate summary for job " + jobId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during summarization for job ID: {}", jobId, e);
            job.setStatus("ERROR_SUMMARIZING");
            job.setStructuredSummaryJson(null);
            jobDescriptionRepository.save(job);
            throw new RuntimeException("Unexpected error during job summarization.", e);
        }
        return jobDescriptionRepository.save(job);
    }
    private String cleanLlmJsonResponse(String rawResponse) throws LlmInteractionException {
        if (rawResponse == null) {
            throw new LlmInteractionException("LLM returned null response.");
        }
        String cleaned=rawResponse.trim();
        if (cleaned.startsWith("```json")) {
            cleaned=cleaned.substring(7);
        }
        if (cleaned.endsWith("```")) {
            cleaned=cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned=cleaned.trim();

        if (cleaned.isEmpty() || !cleaned.startsWith("{") || !cleaned.endsWith("}")) {
            log.warn("LLM response did not seem to contain valid JSON structure after cleaning. Raw: '{}'", rawResponse);
            throw new LlmInteractionException("LLM response was empty or not JSON-like after cleaning. Cleaned attempt: '" + cleaned + "'");
        }
        log.debug("Cleaned LLM response: {}", cleaned);
        return cleaned;
    }
    private void validateJsonStructure(String jsonString, Long id, String type) throws JsonProcessingException {
        if (objectMapper == null) {
            log.warn("ObjectMapper is null, skipping JSON validation.");
            return;
        }
        try {
            objectMapper.readTree(jsonString);
            log.debug("LLM {} JSON structure validated successfully for ID: {}", type, id);
        } catch (JsonProcessingException jsonEx) {
            log.error("LLM {} response is not valid JSON for ID {}. Response JSON string: '{}'", type, id, jsonString, jsonEx);
            throw jsonEx;
        }
    }
    
    public Optional<JobDescription> getJobById(Long jobId) {
        if (jobId == null) {
            log.warn("getJobById called with null ID.");
            return Optional.empty();
        }
        return jobDescriptionRepository.findById(jobId);
    }
    public List<JobDescription> getAllJobs() {
        log.debug("Fetching all job descriptions");
        return jobDescriptionRepository.findAll();
    }
}