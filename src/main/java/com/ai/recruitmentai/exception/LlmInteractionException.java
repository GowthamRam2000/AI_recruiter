package com.ai.recruitmentai.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class LlmInteractionException extends RuntimeException {
    public LlmInteractionException(String message) {
        super(message);
    }

    public LlmInteractionException(String message, Throwable cause) {
        super(message, cause);
    }
}