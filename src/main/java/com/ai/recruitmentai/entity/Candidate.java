package com.ai.recruitmentai.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Data
@NoArgsConstructor
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String candidateIdFromFile;
    private String name;
    @Column(unique = true)
    private String email;
    private String phone;
    private String originalFilePath;
    @Column(columnDefinition = "TEXT")
    private String extractedCvJson;
    private String status;

}