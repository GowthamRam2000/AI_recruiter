package com.ai.recruitmentai.service;
import com.ai.recruitmentai.entity.Application;
import com.ai.recruitmentai.entity.Candidate;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.llm.OllamaClient;
import com.ai.recruitmentai.llm.PromptFactory;
import com.ai.recruitmentai.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
@Service
public class InterviewService {
    private static final Logger log=LoggerFactory.getLogger(InterviewService.class);
    private final ApplicationRepository applicationRepository;
    private final OllamaClient ollamaClient;
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String mailFromAddress;
    @Autowired
    public InterviewService(ApplicationRepository applicationRepository,
                            OllamaClient ollamaClient,
                            JavaMailSender mailSender) {
        this.applicationRepository=applicationRepository;
        this.ollamaClient=ollamaClient;
        this.mailSender=mailSender;
    }
    @Transactional
    public List<Application> sendInvitationsForJob(Long jobId) {
        log.info("Starting process to send interview invitations for Job ID: {}", jobId);
        List<Application> shortlistedApps=applicationRepository.findByJobDescriptionIdAndStatus(jobId, "SHORTLISTED");
        if (shortlistedApps.isEmpty()) {
            log.info("No shortlisted candidates found to invite for Job ID: {}", jobId);
            return List.of();
        }
        log.info("Found {} shortlisted candidates for Job ID: {}", shortlistedApps.size(), jobId);
        List<Application> successfullyProcessed=new ArrayList<>();
        int errorCount=0;
        for (Application app : shortlistedApps) {
            try {
                sendSingleInvitation(app);
                successfullyProcessed.add(app);
            } catch (Exception e) {
                log.error("Failed processing invitation for Application ID {}. Reason: {}", app.getId(), e.getMessage());
                errorCount++;
            }
        }
        log.info("Finished processing invitations for Job ID: {}. Processed: {}, Errors: {}", jobId, successfullyProcessed.size(), errorCount);
        return successfullyProcessed;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW) // Run each email attempt in a new transaction
    protected void sendSingleInvitation(Application application) throws LlmInteractionException, MailException, IllegalStateException {
        Long appId=application.getId();
        Candidate candidate=application.getCandidate();
        JobDescription job=application.getJobDescription();

        if (candidate == null || job == null) {
            log.error("Application ID {} is missing Candidate or JobDescription data.", appId);
            updateApplicationStatus(appId, "ERROR_MISSING_DATA");
            throw new IllegalStateException("Application missing required data: " + appId);
        }
        String candidateEmail=candidate.getEmail();
        String candidateName=StringUtils.hasText(candidate.getName()) ? candidate.getName() : "Candidate";
        String jobTitle=job.getJobTitle();
        if (!StringUtils.hasText(candidateEmail)) {
            log.error("Cannot send invitation for Application ID {}: Candidate email is missing.", appId);
            updateApplicationStatus(appId, "ERROR_MISSING_EMAIL");
            throw new IllegalStateException("Candidate email missing for Application ID: " + appId);
        }
        log.info("Processing invitation for Application ID {}, Candidate: {} ({}), Job: {}", appId, candidateName, candidateEmail, jobTitle);
        try {
            log.debug("Generating email draft for Application ID {}", appId);
            String draftPrompt=PromptFactory.createInterviewEmailDraftPrompt(candidateName, jobTitle);
            String emailBodyDraft=ollamaClient.generate(draftPrompt);
            if (!StringUtils.hasText(emailBodyDraft)) {
                throw new LlmInteractionException("LLM returned empty email body draft for Application ID " + appId);
            }
            log.debug("Generated email body draft for Application ID {}", appId);
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(mailFromAddress);
            message.setTo(candidateEmail);
            message.setSubject("Interview Invitation: " + jobTitle + " at AI Corp");
            String fullEmailBody="""
                    Dear %s,

                    %s

                    Please let us know your availability or if you have any questions.

                    Best regards,
                    The AI Corp Hiring Team
                    """.formatted(candidateName, emailBodyDraft);
            message.setText(fullEmailBody);
            log.info("Attempting to send interview invitation email to {} for Application ID {}", candidateEmail, appId);
            mailSender.send(message);
            log.info("Successfully sent email for Application ID: {}", appId);

            updateApplicationStatus(appId, "INTERVIEW_SCHEDULED");
        } catch (LlmInteractionException e) {
            log.error("LLM Interaction failed during email draft generation for Application ID {}", appId, e);
            updateApplicationStatus(appId, "ERROR_DRAFTING_EMAIL");
            throw e;
        } catch (MailException e) {
            log.error("Mail sending failed for Application ID {} to {}: {}", appId, candidateEmail, e.getMessage(), e);
            updateApplicationStatus(appId, "ERROR_SENDING_EMAIL");
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during invitation processing for Application ID {}", appId, e);
            updateApplicationStatus(appId, "ERROR_SENDING_EMAIL");
            throw new RuntimeException("Unexpected error processing invitation for " + appId, e);
        }
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    protected void updateApplicationStatus(Long applicationId, String status) {
        try {
            applicationRepository.findById(applicationId).ifPresent(app -> {
                app.setStatus(status);
                applicationRepository.save(app);
                log.info("Updated application status to '{}' for ID {}", status, applicationId);
            });
        } catch (Exception e) {
            log.error("Failed to update application status to {} for ID {} after processing", status, applicationId, e);
        }
    }
}