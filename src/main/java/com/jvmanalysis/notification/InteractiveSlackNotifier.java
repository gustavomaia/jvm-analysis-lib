package com.jvmanalysis.notification;

import com.jvmanalysis.comparison.ComparisonResult;
import com.jvmanalysis.comparison.Suggestion;
import com.jvmanalysis.config.JvmAnalysisConfig;
import com.slack.api.Slack;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.*;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Slack notifier with interactive messages for comparison results.
 * Includes action buttons for acknowledging risks and approving suggestions.
 */
public class InteractiveSlackNotifier {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveSlackNotifier.class);
    private final JvmAnalysisConfig.SlackConfig config;
    private final Slack slack;

    public InteractiveSlackNotifier(JvmAnalysisConfig.SlackConfig config) {
        this.config = config;
        this.slack = Slack.getInstance();
    }

    /**
     * Send comparison result with interactive elements.
     */
    public CompletableFuture<Void> sendComparisonAsync(ComparisonResult comparison, String podName, String region) {
        return CompletableFuture.runAsync(() -> {
            try {
                sendComparison(comparison, podName, region);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send Slack comparison", e);
            }
        });
    }

    /**
     * Build and send comprehensive comparison message with risks, opportunities, and suggestions.
     */
    private void sendComparison(ComparisonResult comparison, String podName, String region) throws IOException {
        List<LayoutBlock> blocks = new ArrayList<>();

        // Header
        blocks.add(HeaderBlock.builder()
                .text(PlainTextObject.builder()
                        .text("🔍 JVM Performance Comparison Report")
                        .emoji(true)
                        .build())
                .build());

        blocks.add(SectionBlock.builder()
                .fields(List.of(
                        MarkdownTextObject.builder()
                                .text("*Pod:* " + podName)
                                .build(),
                        MarkdownTextObject.builder()
                                .text("*Region:* " + region)
                                .build(),
                        MarkdownTextObject.builder()
                                .text("*Previous:* " + comparison.getPrevious().getTimestamp())
                                .build(),
                        MarkdownTextObject.builder()
                                .text("*Current:* " + comparison.getCurrent().getTimestamp())
                                .build()
                ))
                .build());

        blocks.add(DividerBlock.builder().build());

        // CRITICAL: Risks (if any)
        if (!comparison.getRisks().isEmpty()) {
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("⚠️ *RISKS DETECTED* - " + comparison.getRisks().size() + " regression(s) need attention")
                            .build())
                    .build());

            for (ComparisonResult.Risk risk : comparison.getRisks()) {
                // Only show top 3 risks in detail
                if (comparison.getRisks().indexOf(risk) < 3) {
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(formatRisk(risk))
                                    .build())
                            .accessory(ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("Acknowledge").build())
                                    .value("risk_" + risk.getCategory())
                                    .actionId("acknowledge_risk")
                                    .style("danger")
                                    .build())
                            .build());
                }
            }

            if (comparison.getRisks().size() > 3) {
                blocks.add(ContextBlock.builder()
                        .elements(List.of(MarkdownTextObject.builder()
                                .text("... and " + (comparison.getRisks().size() - 3) + " more risks")
                                .build()))
                        .build());
            }

            blocks.add(DividerBlock.builder().build());
        }

        // Opportunities (improvements >= 10%)
        if (!comparison.getOpportunities().isEmpty()) {
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("✅ *IMPROVEMENTS* - " + comparison.getOpportunities().size() + " area(s) improved!")
                            .build())
                    .build());

            for (ComparisonResult.Opportunity opp : comparison.getOpportunities()) {
                if (comparison.getOpportunities().indexOf(opp) < 3) {
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(formatOpportunity(opp))
                                    .build())
                            .build());
                }
            }

            blocks.add(DividerBlock.builder().build());
        }

        // ACTIONABLE: Suggestions with complexity/gain
        if (!comparison.getSuggestions().isEmpty()) {
            blocks.add(SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                            .text("💡 *ACTIONABLE SUGGESTIONS* - Sorted by priority")
                            .build())
                    .build());

            // Show top 5 suggestions
            for (Suggestion suggestion : comparison.getSuggestions()) {
                if (comparison.getSuggestions().indexOf(suggestion) < 5) {
                    blocks.add(SectionBlock.builder()
                            .text(MarkdownTextObject.builder()
                                    .text(formatSuggestion(suggestion))
                                    .build())
                            .accessory(ButtonElement.builder()
                                    .text(PlainTextObject.builder().text("View Details").build())
                                    .value("suggestion_" + suggestion.getId())
                                    .actionId("view_suggestion")
                                    .style(suggestion.getPriorityScore() >= 80 ? "primary" : null)
                                    .build())
                            .build());
                }
            }

            if (comparison.getSuggestions().size() > 5) {
                blocks.add(ContextBlock.builder()
                        .elements(List.of(MarkdownTextObject.builder()
                                .text("... and " + (comparison.getSuggestions().size() - 5) + " more suggestions")
                                .build()))
                        .build());
            }

            blocks.add(DividerBlock.builder().build());
        }

        // Actions
        blocks.add(ActionsBlock.builder()
                .elements(List.of(
                        ButtonElement.builder()
                                .text(PlainTextObject.builder().text("View Full Report").build())
                                .url("https://console.cloud.google.com/storage/browser/" +
                                        comparison.getCurrent().getGcsPath())
                                .actionId("view_full_report")
                                .build(),
                        ButtonElement.builder()
                                .text(PlainTextObject.builder().text("Run New Analysis").build())
                                .value("pod_" + podName)
                                .actionId("trigger_analysis")
                                .build()
                ))
                .build());

        // Footer
        blocks.add(ContextBlock.builder()
                .elements(List.of(MarkdownTextObject.builder()
                        .text("Analyzed by JVM Analysis Pipeline | " + comparison.getCurrent().getGitCommitSha())
                        .build()))
                .build());

        // Send payload
        Payload payload = Payload.builder()
                .blocks(blocks)
                .build();

        WebhookResponse response = slack.send(config.getWebhookUrl(), payload);

        if (response.getCode() != 200) {
            throw new IOException("Slack webhook failed: " + response.getCode());
        }

        logger.info("Interactive comparison report sent to Slack");
    }

    /**
     * Format risk for display.
     */
    private String formatRisk(ComparisonResult.Risk risk) {
        String emoji = getSeverityEmoji(risk.getSeverity());
        return String.format("%s *[%s] %s*\n" +
                        "> %s\n" +
                        "> Change: *%+.1f%%* (%s → %s)",
                emoji,
                risk.getSeverity(),
                risk.getCategory(),
                risk.getDescription(),
                risk.getPercentageChange(),
                risk.getPreviousValue(),
                risk.getCurrentValue()
        );
    }

    /**
     * Format opportunity for display.
     */
    private String formatOpportunity(ComparisonResult.Opportunity opp) {
        return String.format("✨ *%s Improved*\n" +
                        "> %s\n" +
                        "> Improvement: *%.1f%%* (%s → %s)",
                opp.getCategory(),
                opp.getDescription(),
                opp.getPercentageImprovement(),
                opp.getPreviousValue(),
                opp.getCurrentValue()
        );
    }

    /**
     * Format suggestion with complexity and gain.
     */
    private String formatSuggestion(Suggestion s) {
        return String.format("%s *%s*\n" +
                        "> %s\n" +
                        "> • Complexity: `%s`\n" +
                        "> • Expected Gain: `%s`\n" +
                        "> • Priority Score: *%d/100*\n" +
                        "> • Estimated Improvement: ~%.1f%%",
                s.getPriorityLabel(),
                s.getTitle(),
                s.getDescription(),
                s.getComplexity().getDescription(),
                s.getExpectedGain().getDescription(),
                s.getPriorityScore(),
                s.getEstimatedImprovementPercent()
        );
    }

    /**
     * Get emoji for risk severity.
     */
    private String getSeverityEmoji(String severity) {
        switch (severity) {
            case "CRITICAL": return "🚨";
            case "HIGH": return "⚠️";
            case "MEDIUM": return "⚡";
            default: return "ℹ️";
        }
    }

    /**
     * Send simple summary message (for high-volume scenarios).
     */
    public CompletableFuture<Void> sendSummaryAsync(ComparisonResult comparison, String podName) {
        return CompletableFuture.runAsync(() -> {
            try {
                String message = String.format(
                        "🔍 *JVM Analysis Complete* - %s\n" +
                                "• Risks: %d  |  Opportunities: %d  |  Suggestions: %d\n" +
                                "%s",
                        podName,
                        comparison.getRisks().size(),
                        comparison.getOpportunities().size(),
                        comparison.getSuggestions().size(),
                        comparison.hasRegressions() ? "⚠️ *ACTION REQUIRED*" : "✅ All good"
                );

                Payload payload = Payload.builder()
                        .text(message)
                        .build();

                slack.send(config.getWebhookUrl(), payload);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send summary", e);
            }
        });
    }
}
