package com.ai.recruitmentai.controller;

import com.ai.recruitmentai.dto.ApplicationResponseDto;
import com.ai.recruitmentai.entity.JobDescription;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.ApplicationService;
import com.ai.recruitmentai.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
@Controller
@RequestMapping("/ui/jobs")
public class JobUIController {
    private static final Logger log=LoggerFactory.getLogger(JobUIController.class);
    private final JobService jobService;
    private final ApplicationService applicationService;

    @Autowired
    public JobUIController(JobService jobService, ApplicationService applicationService) {
        this.jobService=jobService;
        this.applicationService=applicationService;
    }
    @GetMapping
    public String showJobsListPage(Model model) {
        log.info("request received for jobs list page");
        String activePage="jobs";
        try {
            List<JobDescription> jobs=jobService.getAllJobs();
            model.addAttribute("jobs", jobs);
            log.info("found {} jobs to display.", jobs.size());
        } catch (Exception e) {
            log.error("error fetching jobs list: {}", e.getMessage(), e);
            model.addAttribute("jobs", Collections.emptyList());
            model.addAttribute("errorMessage", "Could not load jobs list.");
        }
        model.addAttribute("activePage", activePage);
        return "jobs";
    }
    @GetMapping("/{id}")
    public String showJobDetailsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("Request received for job details page for ID: {}", id);
        String activePage="jobs";
        try {
            JobDescription job=jobService.getJobById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("JobDescription not found with ID: " + id));
            List<ApplicationResponseDto> applications=applicationService.getApplicationsForJob(id)
                    .stream()
                    .map(ApplicationResponseDto::fromEntity)
                    .collect(Collectors.toList());
            model.addAttribute("job", job);
            model.addAttribute("applications", applications);
            model.addAttribute("activePage", activePage);
            log.info("Found job '{}' and {} applications to display.", job.getJobTitle(), applications.size());
            return "job-details";
        } catch (ResourceNotFoundException e) {
            log.warn("Job not found with ID: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Job with ID " + id + " not found.");
            return "redirect:/ui/jobs";
        } catch (Exception e) {
            log.error("Error fetching job details for ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Could not load details for job ID " + id + ".");
            return "redirect:/ui/jobs";
        }
    }
}