package com.ai.recruitmentai.service;
import com.ai.recruitmentai.entity.Application;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.repository.ApplicationRepository;
import com.ai.recruitmentai.repository.JobDescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
@Service
public class ApplicationService {
    private static final Logger log=LoggerFactory.getLogger(ApplicationService.class);
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;
    @Value("${app.shortlisting.default-threshold:80.0}") 
    private double defaultThreshold;

    @Transactional
    public List<Application> shortlistCandidates(Long jobId, Double threshold) {
        log.info("Starting shortlisting process for Job ID: {} with threshold: {}", jobId, threshold);
        if (!jobDescriptionRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("JobDescription not found with ID: " + jobId);
        }
        double effectiveThreshold=(threshold != null) ? threshold : defaultThreshold;
        log.info("Using effective shortlisting threshold: {}", effectiveThreshold);
        List<Application> candidatesToShortlist=applicationRepository
                .findByJobDescriptionIdAndMatchScoreGreaterThanEqualAndStatus(jobId, effectiveThreshold, "MATCHED");

        if (candidatesToShortlist.isEmpty()) {
            log.info("No candidates met the shortlisting criteria (Score >= {} and Status=MATCHED) for Job ID: {}", effectiveThreshold, jobId);
            return List.of();
        }
        log.info("Found {} candidates to shortlist for Job ID: {}", candidatesToShortlist.size(), jobId);
        for (Application app : candidatesToShortlist) {
            app.setStatus("SHORTLISTED");
            log.debug("Updating status to SHORTLISTED for Application ID: {}", app.getId());
        }
        List<Application> shortlistedApplications=applicationRepository.saveAll(candidatesToShortlist);
        log.info("Successfully shortlisted {} candidates for Job ID: {}", shortlistedApplications.size(), jobId);
        return shortlistedApplications;
    }
    public List<Application> getApplicationsForJob(Long jobId) {
        log.debug("Fetching applications for Job ID: {}", jobId);
        if (!jobDescriptionRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("JobDescription not found with ID: " + jobId);
        }
        List<Application> applications=applicationRepository.findByJobDescriptionId(jobId);
        log.debug("Found {} applications for Job ID: {}", applications.size(), jobId);
        return applications;
    }
    public Application getApplicationById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + applicationId));
    }
}