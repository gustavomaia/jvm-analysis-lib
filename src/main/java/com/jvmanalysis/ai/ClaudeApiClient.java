package com.jvmanalysis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jvmanalysis.config.JvmAnalysisConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Async client for Claude API using OkHttp and CompletableFuture.
 */
public class ClaudeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final JvmAnalysisConfig.ClaudeConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeApiClient(JvmAnalysisConfig.ClaudeConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public ClaudeApiClient(JvmAnalysisConfig.ClaudeConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a prompt to Claude API asynchronously.
     *
     * @param prompt The prompt to send
     * @return CompletableFuture with Claude's response
     */
    public CompletableFuture<String> sendPromptAsync(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            String requestBody = buildRequestBody(prompt);
            Request request = new Request.Builder()
                    .url(config.getApiUrl())
                    .addHeader("x-api-key", config.getApiKey())
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

            logger.info("Sending request to Claude API (prompt length: {} chars)", prompt.length());

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.error("Claude API request failed", e);
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            String errorBody = responseBody != null ? responseBody.string() : "No error body";
                            logger.error("Claude API returned error: {} - {}", response.code(), errorBody);
                            future.completeExceptionally(
                                    new IOException("API request failed with code " + response.code() + ": " + errorBody));
                            return;
                        }

                        if (responseBody == null) {
                            future.completeExceptionally(new IOException("Empty response body"));
                            return;
                        }

                        String responseText = responseBody.string();
                        String content = parseResponse(responseText);
                        logger.info("Received Claude response (length: {} chars)", content.length());
                        future.complete(content);

                    } catch (Exception e) {
                        logger.error("Error parsing Claude response", e);
                        future.completeExceptionally(e);
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Error building Claude API request", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Build the request body for Claude API.
     */
    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModel());
        root.put("max_tokens", config.getMaxTokens());

        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parse Claude API response and extract content.
     */
    private String parseResponse(String responseText) throws IOException {
        JsonNode root = objectMapper.readTree(responseText);

        // Check for error
        if (root.has("error")) {
            String errorMessage = root.get("error").get("message").asText();
            throw new IOException("Claude API error: " + errorMessage);
        }

        // Extract content from response
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText();
        }

        throw new IOException("Unexpected response format: " + responseText);
    }

    /**
     * Send multiple prompts in parallel.
     *
     * @param prompts List of prompts
     * @return CompletableFuture with list of responses
     */
    public CompletableFuture<java.util.List<String>> sendPromptsAsync(java.util.List<String> prompts) {
        java.util.List<CompletableFuture<String>> futures = prompts.stream()
                .map(this::sendPromptAsync)
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Shutdown the HTTP client.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
