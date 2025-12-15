package com.jvmanalysis.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmanalysis.config.JvmAnalysisConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async client for Claude API using CompletableFuture.
 */
public class ClaudeClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final JvmAnalysisConfig.ClaudeConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public ClaudeClient(JvmAnalysisConfig.ClaudeConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .build();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(4); // For async operations
    }

    /**
     * Send a message to Claude API asynchronously.
     *
     * @param prompt The analysis prompt
     * @return CompletableFuture with Claude's response
     */
    public CompletableFuture<String> sendMessageAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendMessage(prompt);
            } catch (IOException e) {
                logger.error("Error calling Claude API", e);
                throw new RuntimeException("Failed to call Claude API", e);
            }
        }, executor);
    }

    /**
     * Send a message to Claude API synchronously.
     *
     * @param prompt The analysis prompt
     * @return Claude's response text
     * @throws IOException if the request fails
     */
    public String sendMessage(String prompt) throws IOException {
        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(config.getApiUrl())
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        logger.info("Sending request to Claude API");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Unexpected response: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            // Extract text from content array
            List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }

            throw new IOException("No content in Claude response");
        }
    }

    /**
     * Shutdown the executor when done.
     */
    public void shutdown() {
        if (executor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) executor).shutdown();
        }
    }
}
