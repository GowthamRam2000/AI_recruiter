package com.ai.recruitmentai.controller;
import com.ai.recruitmentai.dto.ApplicationResponseDto;
import java.util.stream.Collectors;
// **-----------------**

import com.ai.recruitmentai.entity.Application;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.ApplicationService;
import com.ai.recruitmentai.service.MatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);
    private final ApplicationService applicationService;
    private final MatchingService matchingService;
    @Autowired
    public ApplicationController(ApplicationService applicationService, MatchingService matchingService) {
        this.applicationService = applicationService;
        this.matchingService = matchingService;
    }
    @GetMapping
    public ResponseEntity<?> getApplicationsForJob(@RequestParam Long jobId) {
        log.debug("received request to get applications for Job ID: {}", jobId);
        try {
            List<Application> applications = applicationService.getApplicationsForJob(jobId);
            List<ApplicationResponseDto> responseDtos = applications.stream()
                    .map(ApplicationResponseDto::fromEntity) 
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responseDtos); 
        } catch (ResourceNotFoundException e) {
            log.warn("get applications failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error getting applications for Job ID {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred."));
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getApplicationById(@PathVariable Long id) {
        log.debug("received request to get Application ID: {}", id);
        try {
            Application application = applicationService.getApplicationById(id);
            return ResponseEntity.ok(application); 
        } catch (ResourceNotFoundException e) {
            log.warn("get application failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error getting Application ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred."));
        }
    }
    @PostMapping("/match")
    public ResponseEntity<?> matchCandidateToJob(@RequestParam Long jobId, @RequestParam Long candidateId) {
        log.info("received request to match candidate ID {} to Job ID {}", candidateId, jobId);
        try {
            Application result = matchingService.matchCandidateToJob(jobId, candidateId);
            ApplicationResponseDto dto = ApplicationResponseDto.fromEntity(result);
            return ResponseEntity.ok(dto);
        } catch (ResourceNotFoundException e) {
            log.warn("Matching failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (LlmInteractionException | IllegalStateException e) {
            log.error("Matching failed for Job ID {} / candidate ID {}: {}", jobId, candidateId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Matching failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error during matching for Job ID {} / candidate ID {}: {}", jobId, candidateId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during matching."));
        }
    }

}