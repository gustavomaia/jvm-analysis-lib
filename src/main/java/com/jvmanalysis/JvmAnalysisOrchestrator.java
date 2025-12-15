package com.jvmanalysis;

import com.jvmanalysis.ai.ClaudeClient;
import com.jvmanalysis.ai.JfrAnalyzer;
import com.jvmanalysis.ai.SourceCodeFetcher;
import com.jvmanalysis.collector.JfrCollector;
import com.jvmanalysis.collector.KubernetesLeaderElection;
import com.jvmanalysis.collector.PodSelectionStrategy;
import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.JfrAnalysisData;
import com.jvmanalysis.notification.SlackNotifier;
import com.jvmanalysis.parser.JfrParser;
import com.jvmanalysis.storage.GcsUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main orchestrator for JVM analysis pipeline with full async processing.
 *
 * Pipeline:
 * 1. Check if pod should collect (leader election)
 * 2. Record JFR dump
 * 3. Parse dump (parallel: CPU, Memory, GC, Threads)
 * 4. Upload dump to GCS (async)
 * 5. Fetch source code for hot methods (async)
 * 6. Analyze with Claude AI (async)
 * 7. Upload report to GCS (async)
 * 8. Send Slack notification (async)
 */
public class JvmAnalysisOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(JvmAnalysisOrchestrator.class);

    private final JvmAnalysisConfig config;
    private final JfrCollector jfrCollector;
    private final JfrParser jfrParser;
    private final ClaudeClient claudeClient;
    private final JfrAnalyzer analyzer;
    private final GcsUploader gcsUploader;
    private final SlackNotifier slackNotifier;
    private final PodSelectionStrategy podSelector;
    private final SourceCodeFetcher sourceCodeFetcher;
    private final KubernetesLeaderElection leaderElection;
    private final ExecutorService executor;

    public JvmAnalysisOrchestrator(JvmAnalysisConfig config) throws Exception {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(4);

        // Initialize components
        this.jfrCollector = new JfrCollector(config.getJfr());
        this.jfrParser = new JfrParser(config.getAnalysis());
        this.claudeClient = new ClaudeClient(config.getClaude());
        this.analyzer = new JfrAnalyzer(claudeClient);
        this.gcsUploader = new GcsUploader(config.getGcs());
        this.slackNotifier = new SlackNotifier(config.getSlack());

        // Initialize source code fetcher
        String githubToken = System.getenv("GITHUB_TOKEN");
        String githubRepo = System.getenv("GITHUB_REPO"); // Format: "owner/repo"
        this.sourceCodeFetcher = new SourceCodeFetcher(githubToken, githubRepo);

        // Initialize leader election if enabled
        String selectionStrategy = System.getenv().getOrDefault("JFR_SELECTION_STRATEGY", "leader-election");
        if ("leader-election".equals(selectionStrategy)) {
            this.leaderElection = KubernetesLeaderElection.fromEnvironment();
            if (this.leaderElection != null) {
                this.leaderElection.start();
                logger.info("Leader election started");
            }
        } else {
            this.leaderElection = null;
        }

        this.podSelector = new PodSelectionStrategy(leaderElection);

        logger.info("JVM Analysis Orchestrator initialized");
    }

    /**
     * Execute the full analysis pipeline asynchronously.
     *
     * @return CompletableFuture that completes when the entire pipeline is done
     */
    public CompletableFuture<AnalysisResult> runAnalysisPipeline() {
        // Check if this pod should collect
        if (!podSelector.shouldCollect()) {
            logger.info("This pod is not selected for JFR collection, skipping");
            return CompletableFuture.completedFuture(null);
        }

        String region = podSelector.getRegion();
        String podName = podSelector.getPodName();

        logger.info("🚀 Starting JFR analysis pipeline for pod: {} in region: {}", podName, region);

        // Step 1: Record JFR dump (blocking, but fast)
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Step 1: Recording JFR dump");
                return jfrCollector.recordAndDump();
            } catch (Exception e) {
                logger.error("Failed to record JFR dump", e);
                throw new RuntimeException(e);
            }
        }, executor)

        // Step 2 & 3: Parse dump and upload to GCS in parallel
        .thenCompose(jfrPath -> {
            logger.info("Step 2: Parsing JFR dump and uploading to GCS");

            CompletableFuture<JfrAnalysisData> parseFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return jfrParser.parse(jfrPath);
                } catch (Exception e) {
                    logger.error("Failed to parse JFR dump", e);
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<String> uploadFuture = gcsUploader.uploadJfrDumpAsync(jfrPath, region, podName);

            return parseFuture.thenCombine(uploadFuture, (data, gcsPath) -> {
                data.setGcsPath(gcsPath);
                return data;
            });
        })

        // Step 4: Fetch source code for hot methods (async)
        .thenCompose(data -> {
            logger.info("Step 3: Fetching source code for hot methods");

            if (data.getCpuProfile() != null && !data.getCpuProfile().getHotMethods().isEmpty()) {
                // Fetch source for top 5 hot methods
                CompletableFuture<?>[] sourceFutures = data.getCpuProfile()
                        .getHotMethods()
                        .stream()
                        .limit(5)
                        .map(method -> sourceCodeFetcher.fetchSourceAsync(method.getClassName())
                                .thenAccept(source -> {
                                    if (source != null) {
                                        logger.info("Fetched source for: {}", method.getClassName());
                                    }
                                }))
                        .toArray(CompletableFuture[]::new);

                return CompletableFuture.allOf(sourceFutures).thenApply(v -> data);
            }

            return CompletableFuture.completedFuture(data);
        })

        // Step 5: Analyze with Claude AI
        .thenCompose(data -> {
            logger.info("Step 4: Analyzing with Claude AI");
            return analyzer.analyzeAsync(data);
        })

        // Step 6: Upload report and send Slack notification in parallel
        .thenCompose(report -> {
            logger.info("Step 5: Uploading report and sending notifications");

            CompletableFuture<String> reportUploadFuture = gcsUploader.uploadReportAsync(
                    report.getSummary(),
                    region,
                    podName
            );

            return reportUploadFuture.thenCompose(reportUrl ->
                    slackNotifier.sendReportAsync(report, region, podName, reportUrl)
                            .thenApply(sent -> new AnalysisResult(report, reportUrl, sent))
            );
        })

        // Handle completion
        .whenComplete((result, error) -> {
            if (error != null) {
                logger.error("❌ Analysis pipeline failed", error);
            } else if (result != null) {
                logger.info("✅ Analysis pipeline completed successfully");
                logger.info("Report URL: {}", result.getReportUrl());
                logger.info("Slack sent: {}", result.isSlackSent());
            }
        });
    }

    /**
     * Run analysis pipeline synchronously (blocks until complete).
     *
     * @return Analysis result
     * @throws Exception if pipeline fails
     */
    public AnalysisResult runAnalysisPipelineSync() throws Exception {
        return runAnalysisPipeline().get();
    }

    /**
     * Shutdown the orchestrator and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down JVM Analysis Orchestrator");

        if (leaderElection != null) {
            leaderElection.stop();
        }

        claudeClient.shutdown();
        executor.shutdown();

        logger.info("Orchestrator shutdown complete");
    }

    /**
     * Result of the analysis pipeline.
     */
    public static class AnalysisResult {
        private final JfrAnalyzer.AnalysisReport report;
        private final String reportUrl;
        private final boolean slackSent;

        public AnalysisResult(JfrAnalyzer.AnalysisReport report, String reportUrl, boolean slackSent) {
            this.report = report;
            this.reportUrl = reportUrl;
            this.slackSent = slackSent;
        }

        public JfrAnalyzer.AnalysisReport getReport() {
            return report;
        }

        public String getReportUrl() {
            return reportUrl;
        }

        public boolean isSlackSent() {
            return slackSent;
        }
    }

    /**
     * Example main method showing how to use the orchestrator.
     */
    public static void main(String[] args) throws Exception {
        // Load configuration (from file, env vars, etc.)
        JvmAnalysisConfig config = new JvmAnalysisConfig();

        // Configure Claude
        JvmAnalysisConfig.ClaudeConfig claudeConfig = new JvmAnalysisConfig.ClaudeConfig();
        claudeConfig.setApiKey(System.getenv("CLAUDE_API_KEY"));
        config.setClaude(claudeConfig);

        // Configure GCS
        JvmAnalysisConfig.GcsConfig gcsConfig = new JvmAnalysisConfig.GcsConfig();
        gcsConfig.setBucketName(System.getenv("GCS_BUCKET"));
        gcsConfig.setProjectId(System.getenv("GCP_PROJECT_ID"));
        gcsConfig.setEnabled(true);
        config.setGcs(gcsConfig);

        // Configure Slack
        JvmAnalysisConfig.SlackConfig slackConfig = new JvmAnalysisConfig.SlackConfig();
        slackConfig.setWebhookUrl(System.getenv("SLACK_WEBHOOK_URL"));
        slackConfig.setEnabled(true);
        config.setSlack(slackConfig);

        // Configure JFR
        JvmAnalysisConfig.JfrConfig jfrConfig = new JvmAnalysisConfig.JfrConfig();
        config.setJfr(jfrConfig);

        // Configure Analysis
        JvmAnalysisConfig.AnalysisConfig analysisConfig = new JvmAnalysisConfig.AnalysisConfig();
        analysisConfig.getPackageWhitelist().add("com.yourcompany"); // Only analyze your code
        config.setAnalysis(analysisConfig);

        // Create orchestrator
        JvmAnalysisOrchestrator orchestrator = new JvmAnalysisOrchestrator(config);

        // Run analysis (async)
        orchestrator.runAnalysisPipeline()
                .thenAccept(result -> {
                    if (result != null) {
                        System.out.println("Analysis complete! Report: " + result.getReportUrl());
                    }
                })
                .exceptionally(error -> {
                    System.err.println("Analysis failed: " + error.getMessage());
                    return null;
                });

        // Keep running (or shutdown after analysis)
        // orchestrator.shutdown();
    }
}
