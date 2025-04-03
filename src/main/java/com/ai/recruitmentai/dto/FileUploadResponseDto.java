package com.ai.recruitmentai.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDto {
    private String message;
    private String fileName;
    private Long entityId;
    private String status;
}