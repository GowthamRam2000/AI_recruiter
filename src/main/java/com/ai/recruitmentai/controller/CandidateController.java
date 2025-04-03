package com.ai.recruitmentai.controller;

import com.ai.recruitmentai.entity.Candidate;
import com.ai.recruitmentai.exception.FileStorageException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.CandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {
    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);
    private final CandidateService candidateService;
    @Autowired
    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCv(@RequestParam("file") MultipartFile file) {
        log.info("received request to upload CV file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "cannot process empty file."));
        }
        if (!MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType())) {
            log.warn("uploaded file is not a PDF: {}", file.getContentType());
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("message", "Invalid file type. Please upload a PDF file."));
        }
        try {
            Candidate savedCandidate = candidateService.storeAndInitiateParsing(file);
            log.info("successfully stored CV file and initiated parsing for Candidate ID: {}", savedCandidate.getId());
            return ResponseEntity.accepted().body(Map.of(
                    "message", "CV upload accepted. Parsing initiated.",
                    "candidateId", savedCandidate.getId(),
                    "fileName", file.getOriginalFilename(),
                    "initialStatus", savedCandidate.getStatus()
            ));
        } catch (FileStorageException e) {
            log.error("failed to store uploaded CV file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "failed to store file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("unexpected error during CV upload for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "An unexpected error occurred during upload."));
        }
    }

    @GetMapping
    public ResponseEntity<List<Candidate>> getAllCandidates() {
        log.debug("received request to get all candidates.");
        List<Candidate> candidates = candidateService.getAllCandidates();
        return ResponseEntity.ok(candidates);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Candidate> getCandidateById(@PathVariable Long id) {
        log.debug("received request to get candidate ID: {}", id);

        Optional<Candidate> candidateOpt = candidateService.getCandidateById(id);
        return candidateOpt
                .map(candidate -> ResponseEntity.ok().body(candidate))
                .orElseGet(() -> {
                    log.warn("candidate not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }
    @GetMapping("/{id}/parsed")
    public ResponseEntity<?> getCandidateParsedData(@PathVariable Long id) {
        log.debug("received request to get parsed data for candidate ID: {}", id);
        Optional<Candidate> candidateOpt = candidateService.getCandidateById(id);
        return candidateOpt
                .map(candidate -> {
                    if ("PARSED".equals(candidate.getStatus()) && candidate.getExtractedCvJson() != null && !candidate.getExtractedCvJson().isBlank()) {
                        log.debug("ceturning parsed JSON data for candidate ID: {}", id);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(candidate.getExtractedCvJson());
                    } else if (!"ERROR_PARSING".equals(candidate.getStatus())) {
                        log.warn("candidate {} found, but parsing not complete or JSON empty (Status: {}).", id, candidate.getStatus());
                        return ResponseEntity.status(HttpStatus.ACCEPTED)
                                .body(Map.of("message", "Parsing not complete or data unavailable for Candidate ID: " + id, "status", candidate.getStatus()));
                    } else {
                        log.error("candidate {} found, but parsing resulted in error (Status: {}).", id, candidate.getStatus());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("message", "Error occurred during parsing for Candidate ID: " + id, "status", candidate.getStatus()));
                    }
                })
                .orElseGet(() -> {
                    log.warn("candidate not found with ID: {} when requesting parsed data.", id);
                    return ResponseEntity.notFound().build(); // Return 404 Not Found
                });
    }
}