package com.ai.recruitmentai.dto; // Updated package name
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchResultDto {
    private Integer match_score;
    private String justification;
}