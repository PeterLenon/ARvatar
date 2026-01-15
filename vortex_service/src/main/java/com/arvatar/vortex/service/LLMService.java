package com.arvatar.vortex.service;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslator;
import com.arvatar.vortex.models.Intention;
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
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static com.arvatar.vortex.models.Intention.CONVERSE;
import static com.arvatar.vortex.models.Intention.NEW_GURU;


@Service
public class LLMService {
    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    private static final int MAX_CONTEXT_LENGTH = 5000;
    private ZooModel<String, float[]> model;
    private HuggingFaceTokenizer tokenizer;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2";

   @PostConstruct
    public void initialize() {
        try {
        logger.info("Loading embedding model: {}", MODEL_NAME);
        tokenizer = HuggingFaceTokenizer.newInstance(MODEL_NAME);
        TextEmbeddingTranslator translator = TextEmbeddingTranslator.builder(tokenizer)
                .optPoolingMode("mean")
                .optNormalize(true)
                .build();

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_NAME)
                .optEngine("PyTorch")
                .optTranslator(translator)
                .build();
        model = ModelZoo.loadModel(criteria);
        logger.info("Embedding model loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize LLMService: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load embedding model: " + e.getMessage(), e);
        }
    }

    public float[] embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Attempted to embed empty or null text");
            return null;
        }
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            // Ensure text is properly sanitized - remove any problematic characters
            String sanitizedText = text.trim();
            if (sanitizedText.isEmpty()) {
                return null;
            }
            return predictor.predict(sanitizedText);
        } catch (Exception e) {
            logger.error("Embedding failed for text (first 100 chars): {}", 
                text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text, e);
            return null;
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

    public Intention getUserIntention(String query, String conversionId, String guruId) throws IOException, InterruptedException {
       String prompt = String.join("\n", "You are an intent parsing assistant.",
               "You infer possible intention from a users request and maps it to only one of the ones provided below. Defaulting to  ",
               Intention.toString(CONVERSE),
               " if you cannot infer a clear intention.",
               "QUESTION:",
               query,
               "INTENT:",
               Intention.toString(Intention.AWAKEN),
               Intention.toString(Intention.CONVERSE),
               Intention.toString(Intention.SLEEP),
               Intention.toString(Intention.VORTEX),
               Intention.toString(Intention.NEW_GURU),
               Intention.toString(Intention.VOLUME_UP),
               Intention.toString(Intention.VOLUME_DOWN),
               Intention.toString(Intention.BRIGHTNESS_UP),
               Intention.toString(Intention.BRIGHTNESS_DOWN),
               Intention.toString(Intention.SPEAK_FAST),
               Intention.toString(Intention.SPEAK_SLOW)
       );
       ObjectNode body = MAPPER.createObjectNode();
       body.put("model", "llama3:instruct");
       body.put("prompt", prompt);
       body.put("stream", false);
       String requestJson = body.toString();

       HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(OLLAMA_URL))
               .header("Content-Type", "application/json")
               .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
               .build();
       HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
       if(response.statusCode() != 200){
           throw new RuntimeException("Ollama error: " + response.statusCode() + " => " + response.body());
       }
       ObjectNode json = (ObjectNode) MAPPER.readTree(response.body());
       String intent = json.get("response").asText();
       return Intention.valueOf(intent);
    }

    private String getAnswerFromExternalLLMServer(String query, String context) throws IOException, InterruptedException {
        String prompt = String.join("\n","You are a retrieval-augmented assistant.",
                "TASK:",
                "Answer the question using ONLY the context below.",
                "If the context does not contain the answer, say:",
                "I don't have enough information in the provided documents.",
                "QUESTION:",
                query,
                "CONTEXT:",
                context);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", "llama3:instruct");           
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
        if (tokenizer != null) {
            tokenizer.close();
        }
        logger.info("Embedding service cleaned up");
    }
}

