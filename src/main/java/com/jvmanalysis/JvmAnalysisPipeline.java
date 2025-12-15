package com.jvmanalysis;

import com.jvmanalysis.ai.ClaudeApiClient;
import com.jvmanalysis.ai.JvmOptimizationAnalyzer;
import com.jvmanalysis.collector.JfrCollector;
import com.jvmanalysis.comparison.ComparisonResult;
import com.jvmanalysis.comparison.SnapshotComparator;
import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.AnalysisSnapshot;
import com.jvmanalysis.model.JfrAnalysisData;
import com.jvmanalysis.model.OptimizationReport;
import com.jvmanalysis.notification.InteractiveSlackNotifier;
import com.jvmanalysis.notification.SlackNotifier;
import com.jvmanalysis.parser.AsyncJfrParser;
import com.jvmanalysis.storage.GcsSnapshotStore;
import com.jvmanalysis.storage.GcsStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Main orchestrator that ties together the entire async pipeline:
 * Record JFR → Upload to GCS → Parse → Compare with Previous → Analyze with Claude → Notify Slack
 *
 * New features:
 * - Historical snapshot storage
 * - Automatic comparison with previous run
 * - Risk detection (regressions)
 * - Opportunity detection (improvements >= 10%)
 * - Actionable suggestions with complexity/gain analysis
 * - Interactive Slack messages
 */
public class JvmAnalysisPipeline {

    private static final Logger logger = LoggerFactory.getLogger(JvmAnalysisPipeline.class);

    private final JvmAnalysisConfig config;
    private final JfrCollector jfrCollector;
    private final AsyncJfrParser jfrParser;
    private final GcsStorage gcsStorage;
    private final ClaudeApiClient claudeClient;
    private final JvmOptimizationAnalyzer analyzer;
    private final SlackNotifier slackNotifier;
    private final GcsSnapshotStore snapshotStore;
    private final SnapshotComparator comparator;
    private final InteractiveSlackNotifier interactiveNotifier;

    public JvmAnalysisPipeline(JvmAnalysisConfig config) throws Exception {
        this.config = config;
        this.jfrCollector = new JfrCollector(config.getJfr());
        this.jfrParser = new AsyncJfrParser(config.getAnalysis());
        this.gcsStorage = new GcsStorage(config.getGcs());
        this.claudeClient = new ClaudeApiClient(config.getClaude());
        this.analyzer = new JvmOptimizationAnalyzer(config, claudeClient);
        this.slackNotifier = new SlackNotifier(config.getSlack());

        // New comparison features
        this.snapshotStore = new GcsSnapshotStore(gcsStorage.getStorage(), config.getGcs().getBucketName());
        this.comparator = new SnapshotComparator();
        this.interactiveNotifier = new InteractiveSlackNotifier(config.getSlack());

        logger.info("JVM Analysis Pipeline initialized with comparison features");
    }

    /**
     * Execute the full analysis pipeline with historical comparison (RECOMMENDED).
     * This method:
     * 1. Records JFR dump
     * 2. Parses and creates snapshot
     * 3. Compares with previous snapshot
     * 4. Detects risks (regressions) and opportunities (improvements >= 10%)
     * 5. Generates actionable suggestions with complexity/gain analysis
     * 6. Sends interactive Slack report
     * 7. Stores snapshot for next comparison
     *
     * @return CompletableFuture with comparison result
     */
    public CompletableFuture<ComparisonResult> executeWithComparisonAsync() {
        logger.info("Starting async JVM analysis pipeline with comparison");

        String podName = getPodName();
        String region = getRegion();

        return recordJfrAsync()
                .thenCompose(this::uploadToGcsAsync)
                .thenCompose(this::parseJfrAsync)
                .thenCompose(data -> {
                    // Create snapshot from parsed data
                    AnalysisSnapshot currentSnapshot = AnalysisSnapshot.fromJfrData(data);
                    currentSnapshot.setPodName(podName);
                    currentSnapshot.setRegion(region);

                    // Get previous snapshot for comparison
                    return snapshotStore.getLatestAsync(podName, region)
                            .thenCompose(previousOpt -> {
                                // Compare if previous exists
                                CompletableFuture<ComparisonResult> comparisonFuture;

                                if (previousOpt.isPresent()) {
                                    logger.info("Comparing with previous snapshot from {}",
                                            previousOpt.get().getTimestamp());
                                    comparisonFuture = comparator.compareAsync(currentSnapshot, previousOpt.get());
                                } else {
                                    logger.info("No previous snapshot found - this is the first run");
                                    // No comparison for first run
                                    ComparisonResult result = new ComparisonResult();
                                    result.setCurrent(currentSnapshot);
                                    comparisonFuture = CompletableFuture.completedFuture(result);
                                }

                                // Store current snapshot for next time
                                return comparisonFuture.thenCompose(result ->
                                        snapshotStore.saveAsync(currentSnapshot)
                                                .thenApply(v -> result)
                                );
                            });
                })
                .thenCompose(result -> {
                    // Send interactive Slack notification
                    return interactiveNotifier.sendComparisonAsync(result, podName, region)
                            .thenApply(v -> result);
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Pipeline with comparison failed", error);
                    } else {
                        logger.info("Pipeline completed: {} risks, {} opportunities, {} suggestions",
                                result.getRisks().size(),
                                result.getOpportunities().size(),
                                result.getSuggestions().size());
                    }
                });
    }

    /**
     * Execute the full analysis pipeline asynchronously (without comparison).
     * Use this for one-off analysis or when you don't need historical tracking.
     *
     * @return CompletableFuture with the optimization report
     */
    public CompletableFuture<OptimizationReport> executeAsync() {
        logger.info("Starting async JVM analysis pipeline");

        return recordJfrAsync()
                .thenCompose(this::uploadToGcsAsync)
                .thenCompose(this::parseJfrAsync)
                .thenCompose(this::analyzeWithClaudeAsync)
                .thenCompose(this::enrichReportAsync)
                .thenCompose(this::notifySlackAsync)
                .whenComplete((report, error) -> {
                    if (error != null) {
                        logger.error("Pipeline failed", error);
                    } else {
                        logger.info("Pipeline completed successfully");
                    }
                });
    }

    /**
     * Step 1: Record JFR dump asynchronously.
     */
    private CompletableFuture<Path> recordJfrAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Recording JFR dump");
                return jfrCollector.recordAndDump();
            } catch (Exception e) {
                throw new RuntimeException("JFR recording failed", e);
            }
        });
    }

    /**
     * Step 2: Upload JFR dump to GCS asynchronously.
     */
    private CompletableFuture<JfrFileContext> uploadToGcsAsync(Path jfrFile) {
        String podName = getPodName();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String remotePath = gcsStorage.generateRemotePath(podName, timestamp);

        return gcsStorage.uploadAsync(jfrFile, remotePath)
                .thenApply(gcsUri -> {
                    logger.info("JFR uploaded to: {}", gcsUri);
                    return new JfrFileContext(jfrFile, gcsUri);
                });
    }

    /**
     * Step 3: Parse JFR dump asynchronously (CPU, Memory, GC, Threads in parallel).
     */
    private CompletableFuture<JfrAnalysisData> parseJfrAsync(JfrFileContext context) {
        return jfrParser.parseAsync(context.localPath)
                .thenApply(data -> {
                    data.setGcsPath(context.gcsUri);
                    logger.info("JFR parsing complete");
                    return data;
                });
    }

    /**
     * Step 4: Analyze with Claude AI asynchronously.
     */
    private CompletableFuture<OptimizationReport> analyzeWithClaudeAsync(JfrAnalysisData data) {
        return analyzer.analyzeAsync(data)
                .thenApply(report -> {
                    logger.info("Claude analysis complete");
                    return report;
                });
    }

    /**
     * Step 5: Enrich report with pod metadata.
     */
    private CompletableFuture<OptimizationReport> enrichReportAsync(OptimizationReport report) {
        return CompletableFuture.supplyAsync(() -> {
            report.setPodName(getPodName());
            report.setRegion(getRegion());
            return report;
        });
    }

    /**
     * Step 6: Send notification to Slack asynchronously.
     */
    private CompletableFuture<OptimizationReport> notifySlackAsync(OptimizationReport report) {
        return slackNotifier.notifyAsync(report)
                .thenApply(v -> {
                    logger.info("Slack notification sent");
                    return report;
                });
    }

    /**
     * Execute just the recording and parsing (no Claude analysis).
     * Useful for collecting data without incurring Claude API costs.
     *
     * @return CompletableFuture with parsed JFR data
     */
    public CompletableFuture<JfrAnalysisData> recordAndParseAsync() {
        return recordJfrAsync()
                .thenCompose(this::uploadToGcsAsync)
                .thenCompose(this::parseJfrAsync);
    }

    /**
     * Analyze an existing JFR file (skip recording).
     *
     * @param jfrFile Path to existing JFR file
     * @return CompletableFuture with optimization report
     */
    public CompletableFuture<OptimizationReport> analyzeExistingJfrAsync(Path jfrFile) {
        return parseJfrAsync(new JfrFileContext(jfrFile, null))
                .thenCompose(this::analyzeWithClaudeAsync)
                .thenCompose(this::enrichReportAsync)
                .thenCompose(this::notifySlackAsync);
    }

    /**
     * Scheduled execution - run analysis at fixed intervals.
     * Returns a future that never completes (runs indefinitely).
     *
     * @param intervalMillis Interval between runs in milliseconds
     * @return CompletableFuture that runs indefinitely
     */
    public CompletableFuture<Void> runScheduled(long intervalMillis) {
        return CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    logger.info("Starting scheduled analysis");
                    executeAsync().join(); // Wait for completion
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException e) {
                    logger.info("Scheduled analysis interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Scheduled analysis failed, will retry", e);
                    try {
                        Thread.sleep(intervalMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    /**
     * Get pod name from environment or hostname.
     */
    private String getPodName() {
        String podName = System.getenv("POD_NAME");
        if (podName != null && !podName.isEmpty()) {
            return podName;
        }

        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-pod";
        }
    }

    /**
     * Get region from environment.
     */
    private String getRegion() {
        String region = System.getenv("REGION");
        if (region != null && !region.isEmpty()) {
            return region;
        }

        // Try GCP metadata (common in GKE)
        String zone = System.getenv("GCP_ZONE");
        if (zone != null && !zone.isEmpty()) {
            return zone;
        }

        return "unknown-region";
    }

    /**
     * Shutdown all services gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down JVM Analysis Pipeline");
        jfrParser.shutdown();
        gcsStorage.shutdown();
        claudeClient.shutdown();
        slackNotifier.shutdown();
    }

    /**
     * Helper class to pass context between pipeline stages.
     */
    private static class JfrFileContext {
        final Path localPath;
        final String gcsUri;

        JfrFileContext(Path localPath, String gcsUri) {
            this.localPath = localPath;
            this.gcsUri = gcsUri;
        }
    }

    /**
     * Builder for JvmAnalysisPipeline with fluent configuration.
     */
    public static class Builder {
        private final JvmAnalysisConfig config = new JvmAnalysisConfig();

        public Builder claudeApiKey(String apiKey) {
            if (config.getClaude() == null) {
                config.setClaude(new JvmAnalysisConfig.ClaudeConfig());
            }
            config.getClaude().setApiKey(apiKey);
            return this;
        }

        public Builder slackWebhook(String webhookUrl) {
            if (config.getSlack() == null) {
                config.setSlack(new JvmAnalysisConfig.SlackConfig());
            }
            config.getSlack().setWebhookUrl(webhookUrl);
            return this;
        }

        public Builder gcsBucket(String bucketName) {
            if (config.getGcs() == null) {
                config.setGcs(new JvmAnalysisConfig.GcsConfig());
            }
            config.getGcs().setBucketName(bucketName);
            return this;
        }

        public Builder gcsProjectId(String projectId) {
            if (config.getGcs() == null) {
                config.setGcs(new JvmAnalysisConfig.GcsConfig());
            }
            config.getGcs().setProjectId(projectId);
            return this;
        }

        public Builder packageWhitelist(java.util.Set<String> packages) {
            if (config.getAnalysis() == null) {
                config.setAnalysis(new JvmAnalysisConfig.AnalysisConfig());
            }
            config.getAnalysis().setPackageWhitelist(packages);
            return this;
        }

        public Builder recordingDuration(java.time.Duration duration) {
            if (config.getJfr() == null) {
                config.setJfr(new JvmAnalysisConfig.JfrConfig());
            }
            config.getJfr().setDuration(duration);
            return this;
        }

        public JvmAnalysisPipeline build() throws Exception {
            // Set defaults if not configured
            if (config.getClaude() == null) {
                config.setClaude(new JvmAnalysisConfig.ClaudeConfig());
            }
            if (config.getSlack() == null) {
                config.setSlack(new JvmAnalysisConfig.SlackConfig());
            }
            if (config.getGcs() == null) {
                config.setGcs(new JvmAnalysisConfig.GcsConfig());
            }
            if (config.getJfr() == null) {
                config.setJfr(new JvmAnalysisConfig.JfrConfig());
            }
            if (config.getAnalysis() == null) {
                config.setAnalysis(new JvmAnalysisConfig.AnalysisConfig());
            }

            return new JvmAnalysisPipeline(config);
        }
    }
}
