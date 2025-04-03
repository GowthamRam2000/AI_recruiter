package com.ai.recruitmentai.dto;
import com.ai.recruitmentai.entity.Application;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class ApplicationResponseDto {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long candidateId;
    private String candidateName;
    private String candidateFileId;
    private Double matchScore;
    private String matchJustification;
    private String status;
    public static ApplicationResponseDto fromEntity(Application app) {
        if (app==null) return null;
        ApplicationResponseDto dto=new ApplicationResponseDto();
        dto.setId(app.getId());
        dto.setMatchScore(app.getMatchScore());
        dto.setMatchJustification(app.getMatchJustification());
        dto.setStatus(app.getStatus());
        if (app.getJobDescription() != null) {
            dto.setJobId(app.getJobDescription().getId());
            dto.setJobTitle(app.getJobDescription().getJobTitle());
        }
        if (app.getCandidate() != null) {
            dto.setCandidateId(app.getCandidate().getId());
            dto.setCandidateName(app.getCandidate().getName());
            dto.setCandidateFileId(app.getCandidate().getCandidateIdFromFile());
        }
        return dto;
    }
}