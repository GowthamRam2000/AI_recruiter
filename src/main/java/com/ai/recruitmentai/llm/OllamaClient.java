package com.ai.recruitmentai.llm;
import com.ai.recruitmentai.exception.LlmInteractionException;
import com.ai.recruitmentai.llm.dto.OllamaRequest;
import com.ai.recruitmentai.llm.dto.OllamaResponse;
import com.fasterxml.jackson.core.JsonProcessingException; 
import com.fasterxml.jackson.databind.ObjectMapper; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*; 
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
@Service
public class OllamaClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private final RestTemplate restTemplate; 
    private final ObjectMapper objectMapper;
    @Value("${ollama.api.url}") 
    private String ollamaApiUrl;
    @Value("${ollama.model.name}") 
    private String ollamaModelName;
    public OllamaClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    public String generate(String prompt) {
        log.info("sending prompt to Ollama model: {}", ollamaModelName);
        log.debug("prompt content (truncated): {}", prompt.substring(0, Math.min(prompt.length(), 200)) + (prompt.length() > 200 ? "..." : ""));
        OllamaRequest requestPayload = new OllamaRequest(ollamaModelName, prompt, false); 
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String requestBodyJson = objectMapper.writeValueAsString(requestPayload);
            HttpEntity<String> entity = new HttpEntity<>(requestBodyJson, headers);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(ollamaApiUrl, entity, String.class);
            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                OllamaResponse ollamaResponse = objectMapper.readValue(responseEntity.getBody(), OllamaResponse.class);
                log.info("received successful response from Ollama.");
                log.debug("Response details: model={}, done={}, duration={}",
                        ollamaResponse.getModel(), ollamaResponse.getDone(), ollamaResponse.getTotalDuration());
                if (ollamaResponse.getResponse() != null && !ollamaResponse.getResponse().isBlank()) {
                    return ollamaResponse.getResponse().trim(); 
                } else {
                    log.error("Ollama response body was OK but contained no 'response' text.");
                    throw new LlmInteractionException("Received empty response text from Ollama.");
                }
            } else {
                log.error("received non-OK status code from Ollama: {} - Body: {}", responseEntity.getStatusCode(), responseEntity.getBody());
                throw new LlmInteractionException("Ollama API returned status code: " + responseEntity.getStatusCode());
            }
        } catch (JsonProcessingException e) {
            log.error("error processing JSON for Ollama request/response", e);
            throw new LlmInteractionException("error processing JSON for Ollama communication.", e);
        } catch (RestClientException e) {
            log.error("error communicating with Ollama API at {}", ollamaApiUrl, e);
            throw new LlmInteractionException("error communicating with Ollama API: " + e.getMessage(), e);
        } catch (Exception e) { // Catch unexpected errors
            log.error("an unexpected error occurred during Ollama interaction", e);
            throw new LlmInteractionException("an unexpected error occurred.", e);
        }
    }
}