package com.jvmanalysis.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmanalysis.model.JfrAnalysisData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Analyzes JFR data using Claude AI to generate optimization recommendations.
 */
public class JfrAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JfrAnalyzer.class);
    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    public JfrAnalyzer(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze JFR data asynchronously and generate recommendations.
     *
     * @param data Parsed JFR analysis data
     * @return CompletableFuture with analysis report
     */
    public CompletableFuture<AnalysisReport> analyzeAsync(JfrAnalysisData data) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Building analysis prompt for Claude");
            String prompt = buildAnalysisPrompt(data);
            return prompt;
        }).thenCompose(prompt -> {
            logger.info("Sending analysis to Claude API");
            return claudeClient.sendMessageAsync(prompt);
        }).thenApply(claudeResponse -> {
            logger.info("Received analysis from Claude, creating report");
            return createReport(data, claudeResponse);
        });
    }

    /**
     * Build a detailed prompt for Claude with JFR analysis data.
     */
    private String buildAnalysisPrompt(JfrAnalysisData data) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert Java performance engineer. Analyze the following JVM profiling data from a Java Flight Recorder (JFR) dump and provide specific, actionable recommendations to improve CPU usage and memory allocation.\n\n");

        // CPU Profile
        if (data.getCpuProfile() != null && !data.getCpuProfile().getHotMethods().isEmpty()) {
            prompt.append("## CPU PROFILING DATA\n\n");
            prompt.append("Total samples: ").append(data.getCpuProfile().getSampleCount()).append("\n\n");
            prompt.append("Top hot methods (highest CPU consumption):\n\n");

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
        if (data.getMemoryProfile() != null && !data.getMemoryProfile().getTopAllocationSites().isEmpty()) {
            prompt.append("\n## MEMORY ALLOCATION DATA\n\n");
            prompt.append(String.format("Total allocations: %d\n", data.getMemoryProfile().getTotalAllocations()));
            prompt.append(String.format("Total bytes allocated: %.2f MB\n\n",
                    data.getMemoryProfile().getTotalBytesAllocated() / (1024.0 * 1024.0)));

            prompt.append("Top allocation sites:\n\n");
            data.getMemoryProfile().getTopAllocationSites().forEach(site -> {
                prompt.append(String.format("- **%s** allocated in %s.%s\n",
                        site.getAllocatedType(),
                        site.getClassName(),
                        site.getMethodName()));
                prompt.append(String.format("  Count: %d allocations, Size: %.2f MB\n",
                        site.getAllocationCount(),
                        site.getTotalBytes() / (1024.0 * 1024.0)));

                if (site.getStackTrace() != null && !site.getStackTrace().isEmpty()) {
                    prompt.append("  Stack trace:\n");
                    String[] lines = site.getStackTrace().split("\n");
                    for (int i = 0; i < Math.min(3, lines.length); i++) {
                        prompt.append("  ").append(lines[i]).append("\n");
                    }
                }
                prompt.append("\n");
            });
        }

        // GC Profile
        if (data.getGcProfile() != null) {
            prompt.append("\n## GARBAGE COLLECTION DATA\n\n");
            prompt.append(String.format("GC Collections: %d\n", data.getGcProfile().getGcCount()));
            prompt.append(String.format("Total pause time: %d ms\n", data.getGcProfile().getTotalPauseTime()));
            prompt.append(String.format("Longest pause: %d ms\n", data.getGcProfile().getLongestPause()));

            if (!data.getGcProfile().getIssues().isEmpty()) {
                prompt.append("\nGC Issues detected:\n");
                data.getGcProfile().getIssues().forEach(issue ->
                        prompt.append("- ").append(issue).append("\n"));
            }
            prompt.append("\n");
        }

        // Thread Profile
        if (data.getThreadProfile() != null && !data.getThreadProfile().getIssues().isEmpty()) {
            prompt.append("\n## THREAD ISSUES\n\n");
            data.getThreadProfile().getIssues().forEach(issue -> {
                prompt.append(String.format("- **%s**: %s\n", issue.getType(), issue.getDescription()));
                if (!issue.getThreadsInvolved().isEmpty()) {
                    prompt.append("  Threads: ").append(String.join(", ", issue.getThreadsInvolved())).append("\n");
                }
            });
            prompt.append("\n");
        }

        prompt.append("\n## REQUESTED ANALYSIS\n\n");
        prompt.append("Please provide:\n\n");
        prompt.append("1. **Critical Issues**: Identify the top 3-5 performance bottlenecks that have the highest impact on system throughput.\n\n");
        prompt.append("2. **CPU Optimization Recommendations**: For each hot method, explain:\n");
        prompt.append("   - Why it's consuming high CPU\n");
        prompt.append("   - Specific code-level optimizations (algorithm improvements, caching, batching, etc.)\n");
        prompt.append("   - Expected impact on throughput\n\n");
        prompt.append("3. **Memory Optimization Recommendations**: For high allocation sites, suggest:\n");
        prompt.append("   - Object reuse strategies\n");
        prompt.append("   - Data structure improvements\n");
        prompt.append("   - Heap size adjustments if needed\n\n");
        prompt.append("4. **GC Tuning**: Based on GC patterns, recommend:\n");
        prompt.append("   - GC algorithm changes if beneficial\n");
        prompt.append("   - JVM flags to reduce pause times\n");
        prompt.append("   - Heap sizing recommendations\n\n");
        prompt.append("5. **Threading Improvements**: If contention is detected, suggest concurrency improvements.\n\n");
        prompt.append("6. **Estimated Impact**: For each recommendation, estimate the potential improvement in CPU usage or memory reduction.\n\n");
        prompt.append("Format your response with clear sections and actionable items. Focus on changes that will improve system throughput and reduce costs in a multi-region GCP Kubernetes deployment.\n");

        return prompt.toString();
    }

    /**
     * Create analysis report from Claude's response.
     */
    private AnalysisReport createReport(JfrAnalysisData data, String claudeResponse) {
        AnalysisReport report = new AnalysisReport();
        report.setJfrData(data);
        report.setClaudeAnalysis(claudeResponse);
        report.setTimestamp(System.currentTimeMillis());

        // Generate summary
        StringBuilder summary = new StringBuilder();
        summary.append("# JVM Performance Analysis Report\n\n");
        summary.append(String.format("**Recording Duration**: %d ms\n\n", data.getDurationMillis()));

        if (data.getCpuProfile() != null) {
            summary.append(String.format("**CPU Samples**: %d\n", data.getCpuProfile().getSampleCount()));
            summary.append(String.format("**Hot Methods Found**: %d\n\n", data.getCpuProfile().getHotMethods().size()));
        }

        if (data.getMemoryProfile() != null) {
            summary.append(String.format("**Total Allocations**: %d (%.2f MB)\n\n",
                    data.getMemoryProfile().getTotalAllocations(),
                    data.getMemoryProfile().getTotalBytesAllocated() / (1024.0 * 1024.0)));
        }

        if (data.getGcProfile() != null) {
            summary.append(String.format("**GC Collections**: %d\n", data.getGcProfile().getGcCount()));
            summary.append(String.format("**GC Pause Time**: %d ms (longest: %d ms)\n\n",
                    data.getGcProfile().getTotalPauseTime(),
                    data.getGcProfile().getLongestPause()));
        }

        summary.append("---\n\n");
        summary.append(claudeResponse);

        report.setSummary(summary.toString());

        return report;
    }

    public static class AnalysisReport {
        private JfrAnalysisData jfrData;
        private String claudeAnalysis;
        private String summary;
        private long timestamp;

        public JfrAnalysisData getJfrData() {
            return jfrData;
        }

        public void setJfrData(JfrAnalysisData jfrData) {
            this.jfrData = jfrData;
        }

        public String getClaudeAnalysis() {
            return claudeAnalysis;
        }

        public void setClaudeAnalysis(String claudeAnalysis) {
            this.claudeAnalysis = claudeAnalysis;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
