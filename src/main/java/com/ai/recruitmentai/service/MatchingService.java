package com.ai.recruitmentai.service;
import com.ai.recruitmentai.dto.MatchResultDto;
import com.ai.recruitmentai.entity.Application;
import com.ai.recruitmentai.entity.Candidate;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.llm.OllamaClient;
import com.ai.recruitmentai.llm.PromptFactory;
import com.ai.recruitmentai.repository.ApplicationRepository;
import com.ai.recruitmentai.repository.CandidateRepository;
import com.ai.recruitmentai.repository.JobDescriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
@Service
public class MatchingService {
    private static final Logger log=LoggerFactory.getLogger(MatchingService.class);
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;
    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private OllamaClient ollamaClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Transactional
    public Application matchCandidateToJob(Long jobId, Long candidateId) {
        log.info("Attempting to match Candidate ID {} to Job ID {}", candidateId, jobId);
        JobDescription job=jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobDescription not found with ID: " + jobId));
        if (job.getStructuredSummaryJson() == null || job.getStructuredSummaryJson().isBlank()) {
            throw new IllegalStateException("JobDescription ID " + jobId + " has not been summarized yet.");
        }
        Candidate candidate=candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with ID: " + candidateId));
        if (candidate.getExtractedCvJson() == null || candidate.getExtractedCvJson().isBlank()) {
            throw new IllegalStateException("Candidate ID " + candidateId + " CV has not been parsed yet.");
        }
        Optional<Application> existingApplicationOpt=applicationRepository.findByJobDescriptionIdAndCandidateId(jobId, candidateId);
        Application application=existingApplicationOpt.orElseGet(() -> {
            Application newApp=new Application();
            newApp.setJobDescription(job);
            newApp.setCandidate(candidate);
            return newApp;
        });
        application.setStatus("MATCHING_STARTED");
        applicationRepository.save(application);
        try {
            String prompt=PromptFactory.createMatchingPrompt(job.getStructuredSummaryJson(), candidate.getExtractedCvJson());
            log.info("Sending JD/CV pair to LLM for matching analysis...");
            String llmResponse=ollamaClient.generate(prompt);
            MatchResultDto matchResult=parseMatchResult(llmResponse);
            if (matchResult == null || matchResult.getMatch_score() == null) {
                throw new LlmInteractionException("Failed to parse match_score from LLM response.");
            }
            application.setMatchScore(matchResult.getMatch_score().doubleValue());
            application.setMatchJustification(matchResult.getJustification());
            application.setStatus("MATCHED");
            log.info("Successfully matched Candidate ID {} to Job ID {}. Score: {}", candidateId, jobId, application.getMatchScore());
        } catch (LlmInteractionException | JsonProcessingException e) {
            log.error("Failed to match Candidate ID {} to Job ID {}. Reason: {}", candidateId, jobId, e.getMessage(), e);
            application.setStatus("ERROR_MATCHING");
            application.setMatchJustification("Error during matching process: " + e.getMessage());
            application.setMatchScore(null); // Ensure score is null on error
        } catch (Exception e) {
            log.error("Unexpected error matching Candidate ID {} to Job ID {}", candidateId, jobId, e);
            application.setStatus("ERROR_MATCHING");
            application.setMatchJustification("Unexpected error during matching process: " + e.getMessage());
            application.setMatchScore(null);
        }
        return applicationRepository.save(application);
    }
    @Transactional
    public void matchAllCandidatesToJob(Long jobId) {
        log.info("Starting batch matching process for Job ID: {}", jobId);
        JobDescription job=jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("JobDescription not found with ID: " + jobId));
        if (job.getStructuredSummaryJson() == null || job.getStructuredSummaryJson().isBlank()) {
            throw new IllegalStateException("Cannot run batch match: JobDescription ID " + jobId + " has not been summarized.");
        }
        List<Candidate> parsedCandidates=candidateRepository.findAll().stream()
                .filter(c -> "PARSED".equals(c.getStatus()) && c.getExtractedCvJson() != null && !c.getExtractedCvJson().isBlank())
                .toList();
        log.info("Found {} parsed candidates to match against Job ID: {}", parsedCandidates.size(), jobId);
        int successCount=0;
        int errorCount=0;
        for (Candidate candidate : parsedCandidates) {
            try {
                matchCandidateToJob(jobId, candidate.getId());
                successCount++;
            } catch (Exception e) {
                log.error("Error initiating match for candidate ID {} to job ID {}. Skipping. Reason: {}",
                        candidate.getId(), jobId, e.getMessage());
                errorCount++;
            }
        }
        log.info("Finished batch matching attempt for Job ID: {}. Initiated/Attempted: {}, Errors during initiation: {}", jobId, successCount, errorCount);

    }

    private MatchResultDto parseMatchResult(String llmJsonResponse) throws JsonProcessingException {
        if (llmJsonResponse == null || llmJsonResponse.isBlank()) {
            throw new JsonProcessingException("LLM returned empty or null response for matching.") {};
        }
        try {
            String cleanedJson=llmJsonResponse.replace("```json", "").replace("```", "").trim();
            if (cleanedJson.isEmpty() || !cleanedJson.startsWith("{") || !cleanedJson.endsWith("}")) {
                throw new LlmInteractionException("LLM returned invalid or non-JSON formatted match result: " + llmJsonResponse);
            }
            return objectMapper.readValue(cleanedJson, MatchResultDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM JSON response for match result. Response: {}", llmJsonResponse, e);
            throw e;
        } catch (LlmInteractionException e) {
            log.error("Error cleaning or validating LLM response for match result: {}", llmJsonResponse, e);
            throw new JsonProcessingException(e.getMessage()){};
        }
    }
}