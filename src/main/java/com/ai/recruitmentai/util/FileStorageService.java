package com.ai.recruitmentai.util;
import com.ai.recruitmentai.exception.FileStorageException; 
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; 
import org.springframework.stereotype.Service; 
import org.springframework.util.StringUtils; 
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
@Service 
public class FileStorageService {
    private static final Logger log=LoggerFactory.getLogger(FileStorageService.class);
    private final Path fileStorageLocation; 
    public FileStorageService(@Value("${app.upload.cv-dir}") String uploadDir) {
        this.fileStorageLocation=Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("File storage location initialized to: {}", this.fileStorageLocation);
    }

    @PostConstruct 
    public void init() {
        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("Created file storage directory or directory already exists: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            log.error("Could not create the directory where the uploaded files will be stored.", ex);
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public Path storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file.");
        }
        String originalFilename=StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        log.debug("Storing file with original name: {}", originalFilename);
        try {
            if (originalFilename.contains("..")) {
                throw new FileStorageException("Filename contains invalid path sequence: " + originalFilename);
            }
            Path targetLocation=this.fileStorageLocation.resolve(originalFilename);
            log.debug("Target storage location for file: {}", targetLocation);
            try (InputStream inputStream=file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
                log.info("Successfully stored file: {}", originalFilename);
            }

            return targetLocation; 

        } catch (IOException ex) {
            log.error("Could not store file {}. Please try again!", originalFilename, ex);
            throw new FileStorageException("Could not store file " + originalFilename + ". Please try again!", ex);
        }
    }
}