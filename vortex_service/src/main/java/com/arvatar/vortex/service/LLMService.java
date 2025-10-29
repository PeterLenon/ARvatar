package com.arvatar.vortex.service;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import com.arvatar.vortex.models.PersonaChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;


@Service
public class LLMService {
    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_CONTEXT_LENGTH = 5000;
    private ZooModel<String, float[]> model;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostConstruct
    public void initialize() {
        try {
            logger.info("Loading embedding model: all-MiniLM-L6-v2");
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface/text-embedding/sentence-transformers/all-MiniLM-L6-v2")
                    .optEngine("Pytorch")
                    .build();
            model = ModelZoo.loadModel(criteria);
            logger.info("Embedding model loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load embedding model", e);
            throw new RuntimeException("Failed to initialize EmbeddingService", e);
        }
    }

    public float[] embedText(String text) {
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return predictor.predict(text);
        } catch (Exception e) {
            logger.error("Failed to generate embedding for text: {}", text, e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    public String generateAnswer(String query, List<PersonaChunk> contextChunks) throws IOException, InterruptedException {
        if (contextChunks == null || contextChunks.isEmpty()) {
            return "I don't have enough information to answer that question based on my training data.";
        }
        logger.debug("Generating answer for query: '{}' using {} context chunks", query, contextChunks.size());
        String context = buildContext(contextChunks);
        String answer = getAnswerFromExternalLLMServer(query, context);
        logger.debug("Generated answer: {}", answer);
        return answer;
    }

    private String getAnswerFromExternalLLMServer(String query, String context) throws IOException, InterruptedException {
        String prompt = String.join("\n","You are a retrieval-augmented assistant.",
                "TASK:",
                "Answer the question using ONLY the context below.",
                "If the context does not contain the answer, say:",
                "I don't have enough information in the provided documents.",
                "Always cite the source chunk like [0], [1], etc. right after each claim.",
                "QUESTION:",
                query,
                "CONTEXT:",
                context,
                "FINAL ANSWER:");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "llama3:instruct");                // e.g. "llama3:instruct"
        body.put("prompt", prompt);
        body.put("stream", false);
        String requestJson = body.toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama error: " + response.statusCode() + " => " + response.body());
        }
        ObjectNode json = (ObjectNode) MAPPER.readTree(response.body());
        logger.debug("Ollama response: {}", json);
        return json.get("response").asText();
    }

    private String buildContext(List<PersonaChunk> chunks) {
        StringBuilder context = new StringBuilder();
        int currentLength = 0;
        for (PersonaChunk chunk : chunks) {
            String transcript = chunk.transcript;
            if (currentLength + transcript.length() > MAX_CONTEXT_LENGTH) {
                int remaining = MAX_CONTEXT_LENGTH - currentLength;
                if (remaining > 0) {
                    context.append(transcript, 0, remaining);
                }
                break;
            }
            context.append(transcript).append("\n\n");
            currentLength += transcript.length() + 2;
        }
        return context.toString().trim();
    }

     @PreDestroy
    public void cleanup() {
        if (model != null) {
            model.close();
        }
        logger.info("Embedding service cleaned up");
    }
}

