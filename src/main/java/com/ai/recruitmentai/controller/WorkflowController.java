package com.ai.recruitmentai.controller;

import com.ai.recruitmentai.entity.Application;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.ApplicationService;
import com.ai.recruitmentai.service.InterviewService;
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
@RequestMapping("/api/workflow")
public class WorkflowController {
    private static final Logger log=LoggerFactory.getLogger(WorkflowController.class);
    private final MatchingService matchingService;
    private final ApplicationService applicationService;
    private final InterviewService interviewService;
    @Autowired
    public WorkflowController(MatchingService matchingService,
                              ApplicationService applicationService,
                              InterviewService interviewService) {
        this.matchingService=matchingService;
        this.applicationService=applicationService;
        this.interviewService=interviewService;
    }

    @PostMapping("/match-all")
    public ResponseEntity<?> triggerBatchMatching(@RequestParam Long jobId) {
        log.info("received request to trigger batch matching for Job ID: {}", jobId);
        try {
            matchingService.matchAllCandidatesToJob(jobId);
            return ResponseEntity.ok(Map.of("message", "batch matching process initiated successfully for Job ID: " + jobId));
        } catch (ResourceNotFoundException e) {
            log.warn("batch matching failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("batch matching failed for Job ID {}: {}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Batch matching prerequisites not met: " + e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error during batch matching for Job ID {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during batch matching."));
        }
    }

    @PostMapping("/shortlist")
    public ResponseEntity<?> triggerShortlisting(@RequestParam Long jobId,
                                                 @RequestParam(required=false) Double threshold) {
        log.info("received request to trigger shortlisting for Job ID: {} with threshold: {}", jobId, threshold != null ? threshold : "default");
        try {
            List<Application> shortlisted=applicationService.shortlistCandidates(jobId, threshold);
            return ResponseEntity.ok(Map.of(
                    "message", "Shortlisting process completed for Job ID: " + jobId,
                    "shortlistedCount", shortlisted.size()
            ));
        } catch (ResourceNotFoundException e) {
            log.warn("shortlisting failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error during shortlisting for Job ID {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during shortlisting."));
        }
    }

    @PostMapping("/send-interviews")
    public ResponseEntity<?> triggerInterviewSending(@RequestParam Long jobId) {
        log.info("Received request to trigger sending interview invitations for Job ID: {}", jobId);
        try {
            List<Application> invitedApps=interviewService.sendInvitationsForJob(jobId);
            return ResponseEntity.ok(Map.of(
                    "message", "Interview invitation process completed for Job ID: " + jobId,
                    "invitationsProcessed", invitedApps.size()
            ));
        } catch (ResourceNotFoundException e) {
            log.warn("Interview sending failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during interview sending process for Job ID {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during interview sending. Check logs for details."));
        }
    }
}