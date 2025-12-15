package com.jvmanalysis.notification;

import com.jvmanalysis.ai.JfrAnalyzer;
import com.jvmanalysis.config.JvmAnalysisConfig;
import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Sends analysis reports to Slack.
 */
public class SlackNotifier {

    private static final Logger logger = LoggerFactory.getLogger(SlackNotifier.class);
    private final JvmAnalysisConfig.SlackConfig config;
    private final Slack slack;

    public SlackNotifier(JvmAnalysisConfig.SlackConfig config) {
        this.config = config;
        this.slack = Slack.getInstance();
    }

    /**
     * Send analysis report to Slack asynchronously.
     *
     * @param report Analysis report
     * @param region Region name
     * @param podName Pod name
     * @param gcsReportUrl URL to full report in GCS
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> sendReportAsync(JfrAnalyzer.AnalysisReport report,
                                                       String region,
                                                       String podName,
                                                       String gcsReportUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendReport(report, region, podName, gcsReportUrl);
            } catch (IOException e) {
                logger.error("Failed to send Slack notification", e);
                return false;
            }
        });
    }

    /**
     * Send analysis report to Slack.
     *
     * @param report Analysis report
     * @param region Region name
     * @param podName Pod name
     * @param gcsReportUrl URL to full report
     * @return true if sent successfully
     * @throws IOException if send fails
     */
    public boolean sendReport(JfrAnalyzer.AnalysisReport report,
                              String region,
                              String podName,
                              String gcsReportUrl) throws IOException {
        if (!config.isEnabled()) {
            logger.info("Slack notifications disabled");
            return false;
        }

        String message = formatSlackMessage(report, region, podName, gcsReportUrl);

        Payload payload = Payload.builder()
                .text(message)
                .build();

        WebhookResponse response = slack.send(config.getWebhookUrl(), payload);

        if (response.getCode() == 200) {
            logger.info("Slack notification sent successfully");
            return true;
        } else {
            logger.error("Slack notification failed: {}", response.getMessage());
            return false;
        }
    }

    /**
     * Format analysis report for Slack.
     */
    private String formatSlackMessage(JfrAnalyzer.AnalysisReport report,
                                      String region,
                                      String podName,
                                      String gcsReportUrl) {
        StringBuilder sb = new StringBuilder();

        sb.append("🔍 *JVM Performance Analysis Report*\n\n");
        sb.append(String.format("*Region:* %s\n", region));
        sb.append(String.format("*Pod:* %s\n", podName));
        sb.append(String.format("*Timestamp:* <!date^%d^{date_short_pretty} {time}|%d>\n\n",
                report.getTimestamp() / 1000, report.getTimestamp()));

        // Add key metrics
        if (report.getJfrData().getCpuProfile() != null) {
            sb.append("*CPU Profile:*\n");
            sb.append(String.format("• Samples: %d\n",
                    report.getJfrData().getCpuProfile().getSampleCount()));

            if (!report.getJfrData().getCpuProfile().getHotMethods().isEmpty()) {
                sb.append("• Top hot method: ");
                var topMethod = report.getJfrData().getCpuProfile().getHotMethods().get(0);
                sb.append(String.format("`%s.%s` (%.1f%%)\n",
                        topMethod.getClassName(),
                        topMethod.getMethodName(),
                        topMethod.getPercentage()));
            }
            sb.append("\n");
        }

        if (report.getJfrData().getMemoryProfile() != null) {
            sb.append("*Memory Profile:*\n");
            sb.append(String.format("• Total allocated: %.1f MB\n",
                    report.getJfrData().getMemoryProfile().getTotalBytesAllocated() / (1024.0 * 1024.0)));

            if (!report.getJfrData().getMemoryProfile().getTopAllocationSites().isEmpty()) {
                sb.append("• Top allocation: ");
                var topAlloc = report.getJfrData().getMemoryProfile().getTopAllocationSites().get(0);
                sb.append(String.format("`%s` in `%s` (%.1f MB)\n",
                        topAlloc.getAllocatedType(),
                        topAlloc.getMethodName(),
                        topAlloc.getTotalBytes() / (1024.0 * 1024.0)));
            }
            sb.append("\n");
        }

        if (report.getJfrData().getGcProfile() != null) {
            sb.append("*GC Stats:*\n");
            sb.append(String.format("• Collections: %d\n",
                    report.getJfrData().getGcProfile().getGcCount()));
            sb.append(String.format("• Total pause: %d ms (longest: %d ms)\n\n",
                    report.getJfrData().getGcProfile().getTotalPauseTime(),
                    report.getJfrData().getGcProfile().getLongestPause()));
        }

        // Add summary of Claude analysis (first 500 chars)
        sb.append("*AI Analysis Summary:*\n");
        String claudeAnalysis = report.getClaudeAnalysis();
        if (claudeAnalysis != null && claudeAnalysis.length() > 0) {
            String summary = claudeAnalysis.substring(0, Math.min(500, claudeAnalysis.length()));
            sb.append(summary);
            if (claudeAnalysis.length() > 500) {
                sb.append("...\n\n");
            }
        }

        // Add link to full report
        if (gcsReportUrl != null && !gcsReportUrl.isEmpty()) {
            sb.append(String.format("\n📄 <https://console.cloud.google.com/storage/browser/%s|View Full Report in GCS>",
                    gcsReportUrl.replace("gs://", "")));
        }

        return sb.toString();
    }
}
