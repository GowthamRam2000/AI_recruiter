package com.ai.recruitmentai.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class JobDescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String jobTitle;
    @Column(columnDefinition = "TEXT")
    private String rawDescription;
    @Column(columnDefinition = "TEXT")
    private String structuredSummaryJson;
    private String status;
}