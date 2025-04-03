package com.ai.recruitmentai.controller;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.FileParsingException;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private static final Logger log=LoggerFactory.getLogger(JobController.class);
    private final JobService jobService;
    @Autowired
    public JobController(JobService jobService) {
        this.jobService=jobService;
    }
    @PostMapping("/load-csv")
    public ResponseEntity<?> loadJobsFromCsv(@RequestParam("file") MultipartFile file) {
        log.info("received request to load jobs from CSV file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "cannot process empty file."));
        }
        try {
            List<JobDescription> savedJobs=jobService.loadJobsFromCsv(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "message", "successfully loaded and saved " + savedJobs.size() + " job descriptions.",
                    "count", savedJobs.size()
            ));
        } catch (FileParsingException e) {
            log.error("Csv Parsing error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Error parsing CSV file: " + e.getMessage()));
        } catch (IOException e) {
            log.error("IO error reading uploaded CSV file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error reading uploaded file."));
        } catch (Exception e) {
            log.error("unexpected error loading jobs from CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred."));
        }
    }
    @PostMapping("/{id}/summarize")
    public ResponseEntity<?> summarizeJob(@PathVariable Long id) {
        log.info("received request to summarize Job ID: {}", id);
        try {
            JobDescription summarizedJob=jobService.summarizeJob(id);
            return ResponseEntity.ok(summarizedJob);
        } catch (ResourceNotFoundException e) {
            log.warn("summarization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (LlmInteractionException | IllegalStateException e) {
            log.error("Summarization failed for Job ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Summarization failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during summarization for Job ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred during summarization."));
        }
    }
    @GetMapping
    public ResponseEntity<List<JobDescription>> getAllJobs() {
        log.debug("REST request to get all JobDescriptions");
        List<JobDescription> list=jobService.getAllJobs();
        return ResponseEntity.ok(list);
    }
    @GetMapping("/{id}")
    public ResponseEntity<JobDescription> getJobById(@PathVariable Long id) {
        log.debug("REST request to get JobDescription : {}", id);
        Optional<JobDescription> jobOpt=jobService.getJobById(id);
        return jobOpt
                .map(job -> ResponseEntity.ok().body(job))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}