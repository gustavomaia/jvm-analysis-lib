package com.jvmanalysis.ai;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Fetches source code for methods found in JFR dumps.
 * Uses a multi-tier approach:
 * 1. Git API (if commit SHA available)
 * 2. Bundled sources in classpath
 * 3. Decompilation (fallback)
 */
public class SourceCodeFetcher {

    private static final Logger logger = LoggerFactory.getLogger(SourceCodeFetcher.class);
    private final OkHttpClient httpClient;
    private final Map<String, String> sourceCache;
    private final String gitCommitSha;
    private final String gitHubToken;
    private final String gitHubRepo; // Format: "owner/repo"

    public SourceCodeFetcher(String gitHubToken, String gitHubRepo) {
        this.httpClient = new OkHttpClient();
        this.sourceCache = new HashMap<>();
        this.gitCommitSha = System.getenv("GIT_COMMIT_SHA");
        this.gitHubToken = gitHubToken;
        this.gitHubRepo = gitHubRepo;

        if (gitCommitSha != null) {
            logger.info("Source code fetcher initialized with Git commit: {}", gitCommitSha);
        } else {
            logger.warn("GIT_COMMIT_SHA not found, Git API won't be available");
        }
    }

    /**
     * Fetch source code for a class asynchronously.
     *
     * @param className Fully qualified class name
     * @return CompletableFuture with source code or null
     */
    public CompletableFuture<String> fetchSourceAsync(String className) {
        return CompletableFuture.supplyAsync(() -> fetchSource(className));
    }

    /**
     * Fetch source code for a class.
     *
     * @param className Fully qualified class name (e.g., "com.example.MyClass")
     * @return Source code or null if not found
     */
    public String fetchSource(String className) {
        // Check cache first
        if (sourceCache.containsKey(className)) {
            logger.debug("Source found in cache: {}", className);
            return sourceCache.get(className);
        }

        String source = null;

        // Strategy 1: Try Git API
        if (gitCommitSha != null && gitHubToken != null && gitHubRepo != null) {
            source = fetchFromGitHub(className);
            if (source != null) {
                logger.info("Source fetched from GitHub: {}", className);
                sourceCache.put(className, source);
                return source;
            }
        }

        // Strategy 2: Try bundled sources
        source = fetchBundledSource(className);
        if (source != null) {
            logger.info("Source found in bundled resources: {}", className);
            sourceCache.put(className, source);
            return source;
        }

        // Strategy 3: Decompilation (basic, for demonstration)
        source = decompile(className);
        if (source != null) {
            logger.info("Source decompiled: {}", className);
            sourceCache.put(className, source);
            return source;
        }

        logger.warn("Source code not found for: {}", className);
        return null;
    }

    /**
     * Fetch source from GitHub API using commit SHA.
     */
    private String fetchFromGitHub(String className) {
        try {
            // Convert class name to file path
            // com.example.MyClass -> src/main/java/com/example/MyClass.java
            String filePath = "src/main/java/" + className.replace('.', '/') + ".java";

            String url = String.format("https://api.github.com/repos/%s/contents/%s?ref=%s",
                    gitHubRepo, filePath, gitCommitSha);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "token " + gitHubToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.debug("GitHub API request failed: {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();

                // Parse JSON to get content (base64 encoded)
                // Simple parsing - in production use Jackson
                int contentIndex = responseBody.indexOf("\"content\":\"");
                if (contentIndex == -1) return null;

                int startIndex = contentIndex + "\"content\":\"".length();
                int endIndex = responseBody.indexOf("\"", startIndex);
                if (endIndex == -1) return null;

                String base64Content = responseBody.substring(startIndex, endIndex)
                        .replace("\\n", "");

                // Decode base64
                byte[] decoded = Base64.getDecoder().decode(base64Content);
                return new String(decoded, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.debug("Error fetching from GitHub: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch source from bundled resources (if sources are included in JAR).
     */
    private String fetchBundledSource(String className) {
        try {
            String resourcePath = "/sources/" + className.replace('.', '/') + ".java";
            InputStream is = getClass().getResourceAsStream(resourcePath);

            if (is == null) {
                // Try without /sources prefix
                resourcePath = "/" + className.replace('.', '/') + ".java";
                is = getClass().getResourceAsStream(resourcePath);
            }

            if (is == null) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            logger.debug("Error reading bundled source: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Basic decompilation placeholder.
     * In production, integrate with Procyon, FernFlower, or CFR.
     */
    private String decompile(String className) {
        try {
            Class<?> clazz = Class.forName(className);

            // This is a simplified version - just shows class structure
            StringBuilder sb = new StringBuilder();
            sb.append("// Decompiled from class file\n");
            sb.append("// Note: This is a basic representation. For full decompilation, integrate Procyon/FernFlower\n\n");

            sb.append("package ").append(clazz.getPackage().getName()).append(";\n\n");
            sb.append("public class ").append(clazz.getSimpleName()).append(" {\n");
            sb.append("    // Methods and fields would be decompiled here\n");
            sb.append("    // To enable full decompilation, add Procyon library\n");
            sb.append("}\n");

            return sb.toString();
        } catch (ClassNotFoundException e) {
            logger.debug("Class not found for decompilation: {}", className);
            return null;
        }
    }

    /**
     * Extract a specific method from source code.
     *
     * @param source Source code
     * @param methodName Method name to extract
     * @return Method source code or full source if method not found
     */
    public String extractMethod(String source, String methodName) {
        if (source == null) return null;

        // Simple method extraction - look for method signature
        String methodPattern = "\\s+" + methodName + "\\s*\\(";
        String[] lines = source.split("\n");

        int methodStart = -1;
        int braceCount = 0;
        boolean inMethod = false;
        StringBuilder methodSource = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (!inMethod && line.matches(".*" + methodPattern + ".*")) {
                methodStart = i;
                inMethod = true;
            }

            if (inMethod) {
                methodSource.append(line).append("\n");

                // Count braces to find method end
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    if (c == '}') braceCount--;
                }

                if (braceCount == 0 && methodStart != i) {
                    // Method ended
                    return methodSource.toString();
                }
            }
        }

        // If method not found, return full source
        return source;
    }

    /**
     * Clear the source code cache.
     */
    public void clearCache() {
        sourceCache.clear();
        logger.info("Source code cache cleared");
    }

    /**
     * Get cache statistics.
     */
    public int getCacheSize() {
        return sourceCache.size();
    }
}
