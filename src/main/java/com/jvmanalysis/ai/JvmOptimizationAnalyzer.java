package com.jvmanalysis.ai;

import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.JfrAnalysisData;
import com.jvmanalysis.model.OptimizationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Uses Claude AI to analyze JFR data and provide optimization recommendations.
 */
public class JvmOptimizationAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JvmOptimizationAnalyzer.class);
    private final ClaudeApiClient claudeClient;
    private final JvmAnalysisConfig config;

    public JvmOptimizationAnalyzer(JvmAnalysisConfig config, ClaudeApiClient claudeClient) {
        this.config = config;
        this.claudeClient = claudeClient;
    }

    /**
     * Analyze JFR data asynchronously and generate optimization report.
     *
     * @param data JFR analysis data
     * @return CompletableFuture with optimization report
     */
    public CompletableFuture<OptimizationReport> analyzeAsync(JfrAnalysisData data) {
        logger.info("Starting async JVM optimization analysis");

        return CompletableFuture.supplyAsync(() -> buildAnalysisPrompt(data))
                .thenCompose(prompt -> claudeClient.sendPromptAsync(prompt))
                .thenApply(response -> {
                    OptimizationReport report = new OptimizationReport();
                    report.setJfrData(data);
                    report.setClaudeAnalysis(response);
                    report.setTimestamp(java.time.Instant.now());
                    logger.info("Analysis complete");
                    return report;
                });
    }

    /**
     * Build a comprehensive prompt for Claude based on JFR data.
     */
    private String buildAnalysisPrompt(JfrAnalysisData data) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a JVM performance expert. Analyze this Java Flight Recorder (JFR) data and provide specific, actionable optimization recommendations.\n\n");
        prompt.append("Focus on:\n");
        prompt.append("1. CPU optimization - reducing hot method execution time\n");
        prompt.append("2. Memory allocation optimization - reducing GC pressure\n");
        prompt.append("3. GC tuning recommendations\n");
        prompt.append("4. Thread-related issues\n\n");

        prompt.append("## Recording Details\n");
        prompt.append("- Duration: ").append(data.getDurationMillis()).append("ms\n");
        prompt.append("- File: ").append(data.getJfrFilePath()).append("\n\n");

        // CPU Profile
        if (data.getCpuProfile() != null) {
            prompt.append("## CPU Profile\n");
            prompt.append("Total samples: ").append(data.getCpuProfile().getSampleCount()).append("\n\n");
            prompt.append("### Top Hot Methods:\n");

            data.getCpuProfile().getHotMethods().forEach(method -> {
                prompt.append(String.format("- **%s.%s** - %.2f%% (%d samples)\n",
                        method.getClassName(),
                        method.getMethodName(),
                        method.getPercentage(),
                        method.getSamples()));
                if (method.getStackTrace() != null && !method.getStackTrace().isEmpty()) {
                    prompt.append("  Stack trace:\n");
                    String[] lines = method.getStackTrace().split("\n");
                    for (int i = 0; i < Math.min(5, lines.length); i++) {
                        prompt.append("  ").append(lines[i]).append("\n");
                    }
                }
                prompt.append("\n");
            });
        }

        // Memory Profile
        if (data.getMemoryProfile() != null) {
            prompt.append("## Memory Profile\n");
            prompt.append(String.format("Total allocations: %,d\n", data.getMemoryProfile().getTotalAllocations()));
            prompt.append(String.format("Total bytes allocated: %.2f MB\n\n",
                    data.getMemoryProfile().getTotalBytesAllocated() / (1024.0 * 1024.0)));

            prompt.append("### Top Allocation Sites:\n");
            data.getMemoryProfile().getTopAllocationSites().forEach(site -> {
                prompt.append(String.format("- **%s** in %s.%s - %,d allocations, %.2f MB\n",
                        site.getAllocatedType(),
                        site.getClassName(),
                        site.getMethodName(),
                        site.getAllocationCount(),
                        site.getTotalBytes() / (1024.0 * 1024.0)));
            });
            prompt.append("\n");
        }

        // GC Profile
        if (data.getGcProfile() != null) {
            prompt.append("## GC Profile\n");
            prompt.append(String.format("GC count: %d\n", data.getGcProfile().getGcCount()));
            prompt.append(String.format("Total pause time: %d ms\n", data.getGcProfile().getTotalPauseTime()));
            prompt.append(String.format("Longest pause: %d ms\n", data.getGcProfile().getLongestPause()));
            if (data.getGcProfile().getGcType() != null) {
                prompt.append(String.format("GC type: %s\n", data.getGcProfile().getGcType()));
            }
            if (!data.getGcProfile().getIssues().isEmpty()) {
                prompt.append("\nDetected issues:\n");
                data.getGcProfile().getIssues().forEach(issue ->
                        prompt.append("- ").append(issue).append("\n"));
            }
            prompt.append("\n");
        }

        // Thread Profile
        if (data.getThreadProfile() != null && !data.getThreadProfile().getIssues().isEmpty()) {
            prompt.append("## Thread Issues\n");
            data.getThreadProfile().getIssues().forEach(issue -> {
                prompt.append(String.format("- **%s**: %s\n", issue.getType(), issue.getDescription()));
                if (!issue.getThreadsInvolved().isEmpty()) {
                    prompt.append("  Threads: ").append(String.join(", ", issue.getThreadsInvolved())).append("\n");
                }
            });
            prompt.append("\n");
        }

        prompt.append("## Your Task\n");
        prompt.append("Based on this data, provide:\n\n");
        prompt.append("1. **Critical Issues** - Most important performance problems to fix immediately\n");
        prompt.append("2. **CPU Optimizations** - Specific recommendations to reduce CPU usage in hot methods\n");
        prompt.append("3. **Memory Optimizations** - Ways to reduce allocations and GC pressure\n");
        prompt.append("4. **GC Tuning** - Recommendations for GC configuration (heap size, GC algorithm, flags)\n");
        prompt.append("5. **Thread Optimization** - Solutions for any threading issues\n");
        prompt.append("6. **Estimated Impact** - Expected performance improvement from each recommendation\n\n");
        prompt.append("Format your response in Markdown with clear sections and bullet points. Be specific and actionable.\n");

        return prompt.toString();
    }

    /**
     * Analyze multiple JFR datasets in parallel.
     *
     * @param dataList List of JFR analysis data
     * @return CompletableFuture with list of optimization reports
     */
    public CompletableFuture<java.util.List<OptimizationReport>> analyzeMultipleAsync(
            java.util.List<JfrAnalysisData> dataList) {

        java.util.List<CompletableFuture<OptimizationReport>> futures = dataList.stream()
                .map(this::analyzeAsync)
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList()));
    }
}
