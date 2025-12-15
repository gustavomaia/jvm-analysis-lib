package com.jvmanalysis.example;

import com.jvmanalysis.JvmAnalysisPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Example showing how to integrate JVM analysis into a gRPC service.
 */
public class GrpcServiceExample {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceExample.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting gRPC service with JVM analysis");

        // Initialize the analysis pipeline
        JvmAnalysisPipeline pipeline = new JvmAnalysisPipeline.Builder()
                .claudeApiKey(getEnvOrDefault("CLAUDE_API_KEY", ""))
                .slackWebhook(getEnvOrDefault("SLACK_WEBHOOK_URL", ""))
                .gcsBucket(getEnvOrDefault("JFR_BUCKET", "jfr-dumps"))
                .gcsProjectId(getEnvOrDefault("GCP_PROJECT", "my-project"))
                .packageWhitelist(Set.of(
                        "com.mycompany",  // Your application packages
                        "com.example"
                ))
                .recordingDuration(Duration.ofMinutes(1))
                .build();

        // Option 1: Run scheduled analysis every 6 hours
        logger.info("Starting scheduled JVM analysis (every 6 hours)");
        pipeline.runScheduled(Duration.ofHours(6).toMillis());

        // Option 2: On-demand analysis (e.g., triggered by an HTTP endpoint)
        // setupOnDemandTrigger(pipeline);

        // Option 3: Analyze on threshold (high CPU, GC issues, etc.)
        // monitorAndAnalyze(pipeline);

        // Keep the service running
        new CountDownLatch(1).await();
    }

    /**
     * Example: On-demand analysis triggered by HTTP endpoint.
     */
    private static void setupOnDemandTrigger(JvmAnalysisPipeline pipeline) {
        // You would typically expose this via your HTTP framework (Spring, Javalin, etc.)
        // Example pseudo-code:
        /*
        httpServer.post("/admin/analyze-jvm", (req, res) -> {
            pipeline.executeAsync()
                    .thenAccept(report -> {
                        res.status(200).json(Map.of(
                                "status", "success",
                                "report_url", report.getJfrData().getGcsPath()
                        ));
                    })
                    .exceptionally(error -> {
                        res.status(500).json(Map.of("error", error.getMessage()));
                        return null;
                    });
        });
        */
    }

    /**
     * Example: Conditional analysis based on metrics.
     */
    private static void monitorAndAnalyze(JvmAnalysisPipeline pipeline) {
        // Monitor JVM metrics and trigger analysis when thresholds are exceeded
        /*
        Metrics.onHighCpu(cpuPercent -> {
            if (cpuPercent > 80) {
                logger.warn("High CPU detected: {}%, triggering analysis", cpuPercent);
                pipeline.executeAsync();
            }
        });

        Metrics.onLongGcPause(pauseMs -> {
            if (pauseMs > 100) {
                logger.warn("Long GC pause: {}ms, triggering analysis", pauseMs);
                pipeline.executeAsync();
            }
        });
        */
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
