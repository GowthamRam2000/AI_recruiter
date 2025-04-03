package com.ai.recruitmentai.dto;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
@Data
@NoArgsConstructor
public class CvDataDto {
    private String name;
    private String email;
    private String phone;
    private List<Map<String, String>> education;
    private List<Map<String, String>> work_experience;
    private List<String> skills;
    private List<String> certifications;
    private List<String> achievements;
}