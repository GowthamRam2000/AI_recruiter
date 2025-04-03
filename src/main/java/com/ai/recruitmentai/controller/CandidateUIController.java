package com.ai.recruitmentai.controller;

import com.ai.recruitmentai.entity.Candidate;
import com.ai.recruitmentai.exception.ResourceNotFoundException;
import com.ai.recruitmentai.service.CandidateService;
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

@Controller
@RequestMapping("/ui/candidates")
public class CandidateUIController {
    private static final Logger log=LoggerFactory.getLogger(CandidateUIController.class);
    private final CandidateService candidateService;

    @Autowired
    public CandidateUIController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public String showCandidatesListPage(Model model) {
        log.info("Request received for candidates list page");
        String activePage = "candidates";
        try {
            List<Candidate> candidates = candidateService.getAllCandidates();
            model.addAttribute("candidates", candidates);
            log.info("Found {} candidates to display.", candidates.size());
        } catch (Exception e) {
            log.error("Error fetching candidates list: {}", e.getMessage(), e);
            model.addAttribute("candidates", Collections.emptyList());
            model.addAttribute("errorMessage", "Could not load candidates list.");
        }
        model.addAttribute("activePage", activePage);
        return "candidates";
    }
    @GetMapping("/{id}")
    public String showCandidateDetailsPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.info("request received for candidate details page for ID: {}", id);
        String activePage = "candidates";
        try {
            Candidate candidate = candidateService.getCandidateById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with ID: " + id));
            model.addAttribute("candidate", candidate);
            model.addAttribute("activePage", activePage);
            log.info("Found candidate with File ID: {} to display.", candidate.getCandidateIdFromFile());
            return "candidate-details";
        } catch (ResourceNotFoundException e) {
            log.warn("Candidate not found with ID: {}", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Candidate with ID " + id + " not found.");
            return "redirect:/ui/candidates";
        } catch (Exception e) {
            log.error("Error fetching candidate details for ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Could not load details for candidate ID " + id + ".");
            return "redirect:/ui/candidates";
        }
    }
}