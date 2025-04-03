package com.ai.recruitmentai.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
public class JobSummaryDto {
    private List<String> required_skills;
    private String experience_years;
    private List<String> qualifications;
    private List<String> responsibilities;
}