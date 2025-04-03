package com.ai.recruitmentai.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FileParsingException extends RuntimeException {
    public FileParsingException(String message) {
        super(message);
    }
    public FileParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}