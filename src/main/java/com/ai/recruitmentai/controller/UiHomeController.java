package com.ai.recruitmentai.controller; // Or .ui
import com.ai.recruitmentai.repository.CandidateRepository;
import com.ai.recruitmentai.repository.JobDescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
@Controller
@RequestMapping("/")
public class UiHomeController {
    private static final Logger log=LoggerFactory.getLogger(UiHomeController.class);
    @Autowired(required=false)
    private JobDescriptionRepository jobRepository;
    @Autowired(required=false)
    private CandidateRepository candidateRepository;
    @GetMapping(value={"/", "/ui", "/index"})
    public String home(Model model) {
        log.info("request for home page");
        long jobCount=0L;
        long candidateCount=0L;
        if (jobRepository != null) {
            try {
                jobCount=jobRepository.count();
            } catch (Exception e) {
                log.error("error counting jobs: {}", e.getMessage());
            }
        }
        if (candidateRepository != null) {
            try {
                candidateCount=candidateRepository.count();
            } catch (Exception e) {
                log.error("error counting candidates: {}", e.getMessage());
            }
        }
        model.addAttribute("jobCount", jobCount);
        model.addAttribute("candidateCount", candidateCount);
        model.addAttribute("activePage", "home");
        return "index";
    }
}