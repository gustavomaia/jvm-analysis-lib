package com.jvmanalysis.comparison;

import com.jvmanalysis.model.AnalysisSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Compares two analysis snapshots and detects:
 * - Risks (regressions that need attention)
 * - Opportunities (improvements >= 10%)
 * - Suggestions (actionable optimizations with complexity/gain analysis)
 */
public class SnapshotComparator {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotComparator.class);

    // Thresholds
    private static final double IMPROVEMENT_THRESHOLD = 10.0; // 10% improvement
    private static final double REGRESSION_THRESHOLD = 5.0;    // 5% regression is a risk

    /**
     * Compare two snapshots asynchronously.
     */
    public CompletableFuture<ComparisonResult> compareAsync(AnalysisSnapshot current, AnalysisSnapshot previous) {
        return CompletableFuture.supplyAsync(() -> compare(current, previous));
    }

    /**
     * Compare two snapshots and generate detailed diff with risks and opportunities.
     */
    public ComparisonResult compare(AnalysisSnapshot current, AnalysisSnapshot previous) {
        logger.info("Comparing snapshots: {} vs {}", current.getSnapshotId(), previous.getSnapshotId());

        ComparisonResult result = new ComparisonResult();
        result.setCurrent(current);
        result.setPrevious(previous);

        // Compare each category
        compareCpu(current, previous, result);
        compareMemory(current, previous, result);
        compareGc(current, previous, result);
        compareThreads(current, previous, result);

        // Generate suggestions based on findings
        generateSuggestions(result);

        // Set flags
        result.setHasRegressions(!result.getRisks().isEmpty());
        result.setHasSignificantImprovements(result.getOpportunities().stream()
                .anyMatch(o -> o.getPercentageImprovement() >= IMPROVEMENT_THRESHOLD));

        logger.info("Comparison complete: {} risks, {} opportunities, {} suggestions",
                result.getRisks().size(), result.getOpportunities().size(), result.getSuggestions().size());

        return result;
    }

    /**
     * Compare CPU metrics.
     */
    private void compareCpu(AnalysisSnapshot current, AnalysisSnapshot previous, ComparisonResult result) {
        if (current.getCpu() == null || previous.getCpu() == null) return;

        // Compare top CPU method percentage
        if (current.getTopCpuPercentage() > 0 && previous.getTopCpuPercentage() > 0) {
            double change = calculatePercentageChange(
                    previous.getTopCpuPercentage(),
                    current.getTopCpuPercentage()
            );

            if (change > REGRESSION_THRESHOLD) {
                // CPU usage increased - potential risk
                String severity = change > 20 ? "CRITICAL" : change > 10 ? "HIGH" : "MEDIUM";
                result.getRisks().add(new ComparisonResult.Risk(
                        "CPU",
                        severity,
                        "Top CPU method '" + current.getTopCpuMethod() + "' increased from " +
                                String.format("%.1f%% to %.1f%%", previous.getTopCpuPercentage(), current.getTopCpuPercentage()),
                        change,
                        "Top CPU Method %",
                        previous.getTopCpuPercentage(),
                        current.getTopCpuPercentage()
                ));
            } else if (change < -IMPROVEMENT_THRESHOLD) {
                // CPU usage decreased - opportunity!
                result.getOpportunities().add(new ComparisonResult.Opportunity(
                        "CPU",
                        "Top CPU method improved from " +
                                String.format("%.1f%% to %.1f%%", previous.getTopCpuPercentage(), current.getTopCpuPercentage()),
                        Math.abs(change),
                        "Top CPU Method %",
                        previous.getTopCpuPercentage(),
                        current.getTopCpuPercentage()
                ));
            }
        }

        // Compare number of hot methods
        double hotMethodChange = calculatePercentageChange(
                previous.getCpu().getMethodsAbove5Percent(),
                current.getCpu().getMethodsAbove5Percent()
        );

        if (hotMethodChange > 20) {
            result.getRisks().add(new ComparisonResult.Risk(
                    "CPU",
                    "HIGH",
                    "Number of hot methods (>5% CPU) increased from " +
                            previous.getCpu().getMethodsAbove5Percent() + " to " +
                            current.getCpu().getMethodsAbove5Percent(),
                    hotMethodChange,
                    "Hot Methods Count",
                    previous.getCpu().getMethodsAbove5Percent(),
                    current.getCpu().getMethodsAbove5Percent()
            ));
        }
    }

    /**
     * Compare memory metrics.
     */
    private void compareMemory(AnalysisSnapshot current, AnalysisSnapshot previous, ComparisonResult result) {
        if (current.getMemory() == null || previous.getMemory() == null) return;

        // Compare total bytes allocated
        double memChange = calculatePercentageChange(
                previous.getMemory().getTotalBytesAllocated(),
                current.getMemory().getTotalBytesAllocated()
        );

        if (memChange > REGRESSION_THRESHOLD) {
            String severity = memChange > 30 ? "CRITICAL" : memChange > 15 ? "HIGH" : "MEDIUM";
            result.getRisks().add(new ComparisonResult.Risk(
                    "MEMORY",
                    severity,
                    "Memory allocation increased by " + String.format("%.1f%%", memChange) +
                            " (" + formatBytes(previous.getMemory().getTotalBytesAllocated()) +
                            " → " + formatBytes(current.getMemory().getTotalBytesAllocated()) + ")",
                    memChange,
                    "Total Memory Allocated",
                    formatBytes(previous.getMemory().getTotalBytesAllocated()),
                    formatBytes(current.getMemory().getTotalBytesAllocated())
            ));
        } else if (memChange < -IMPROVEMENT_THRESHOLD) {
            result.getOpportunities().add(new ComparisonResult.Opportunity(
                    "MEMORY",
                    "Memory allocation reduced by " + String.format("%.1f%%", Math.abs(memChange)),
                    Math.abs(memChange),
                    "Total Memory Allocated",
                    formatBytes(previous.getMemory().getTotalBytesAllocated()),
                    formatBytes(current.getMemory().getTotalBytesAllocated())
            ));
        }

        // Compare top allocation site
        if (current.getTopAllocationBytes() > 0 && previous.getTopAllocationBytes() > 0) {
            double topAllocChange = calculatePercentageChange(
                    previous.getTopAllocationBytes(),
                    current.getTopAllocationBytes()
            );

            if (topAllocChange > 20) {
                result.getRisks().add(new ComparisonResult.Risk(
                        "MEMORY",
                        "HIGH",
                        "Top allocation site '" + current.getTopAllocationMethod() + "' increased by " +
                                String.format("%.1f%%", topAllocChange),
                        topAllocChange,
                        "Top Allocation Site",
                        formatBytes(previous.getTopAllocationBytes()),
                        formatBytes(current.getTopAllocationBytes())
                ));
            }
        }
    }

    /**
     * Compare GC metrics.
     */
    private void compareGc(AnalysisSnapshot current, AnalysisSnapshot previous, ComparisonResult result) {
        if (current.getGc() == null || previous.getGc() == null) return;

        // Compare GC pause time
        double pauseChange = calculatePercentageChange(
                previous.getGc().getTotalPauseTimeMs(),
                current.getGc().getTotalPauseTimeMs()
        );

        if (pauseChange > REGRESSION_THRESHOLD) {
            String severity = pauseChange > 30 ? "CRITICAL" : pauseChange > 15 ? "HIGH" : "MEDIUM";
            result.getRisks().add(new ComparisonResult.Risk(
                    "GC",
                    severity,
                    "GC pause time increased by " + String.format("%.1f%%", pauseChange) +
                            " (" + previous.getGc().getTotalPauseTimeMs() + "ms → " +
                            current.getGc().getTotalPauseTimeMs() + "ms)",
                    pauseChange,
                    "Total GC Pause Time",
                    previous.getGc().getTotalPauseTimeMs() + "ms",
                    current.getGc().getTotalPauseTimeMs() + "ms"
            ));
        } else if (pauseChange < -IMPROVEMENT_THRESHOLD) {
            result.getOpportunities().add(new ComparisonResult.Opportunity(
                    "GC",
                    "GC pause time reduced by " + String.format("%.1f%%", Math.abs(pauseChange)),
                    Math.abs(pauseChange),
                    "Total GC Pause Time",
                    previous.getGc().getTotalPauseTimeMs() + "ms",
                    current.getGc().getTotalPauseTimeMs() + "ms"
            ));
        }

        // Check longest pause
        if (current.getGc().getLongestPauseMs() > previous.getGc().getLongestPauseMs() * 1.5) {
            result.getRisks().add(new ComparisonResult.Risk(
                    "GC",
                    "MEDIUM",
                    "Longest GC pause increased from " +
                            previous.getGc().getLongestPauseMs() + "ms to " +
                            current.getGc().getLongestPauseMs() + "ms",
                    calculatePercentageChange(previous.getGc().getLongestPauseMs(), current.getGc().getLongestPauseMs()),
                    "Longest GC Pause",
                    previous.getGc().getLongestPauseMs() + "ms",
                    current.getGc().getLongestPauseMs() + "ms"
            ));
        }
    }

    /**
     * Compare thread metrics.
     */
    private void compareThreads(AnalysisSnapshot current, AnalysisSnapshot previous, ComparisonResult result) {
        if (current.getThreads() == null || previous.getThreads() == null) return;

        // Check for new deadlocks
        if (current.getThreads().getDeadlocksDetected() > previous.getThreads().getDeadlocksDetected()) {
            result.getRisks().add(new ComparisonResult.Risk(
                    "THREADS",
                    "CRITICAL",
                    "New deadlocks detected: " + current.getThreads().getDeadlocksDetected(),
                    0,
                    "Deadlocks",
                    previous.getThreads().getDeadlocksDetected(),
                    current.getThreads().getDeadlocksDetected()
            ));
        }

        // Check contention increase
        double contentionChange = calculatePercentageChange(
                previous.getThreads().getContentionEvents(),
                current.getThreads().getContentionEvents()
        );

        if (contentionChange > 25) {
            result.getRisks().add(new ComparisonResult.Risk(
                    "THREADS",
                    "HIGH",
                    "Thread contention increased by " + String.format("%.1f%%", contentionChange),
                    contentionChange,
                    "Contention Events",
                    previous.getThreads().getContentionEvents(),
                    current.getThreads().getContentionEvents()
            ));
        }
    }

    /**
     * Generate actionable suggestions based on detected risks and opportunities.
     */
    private void generateSuggestions(ComparisonResult result) {
        List<Suggestion> suggestions = new ArrayList<>();

        // Suggestions for CPU risks
        for (ComparisonResult.Risk risk : result.getRisks()) {
            if ("CPU".equals(risk.getCategory())) {
                if (risk.getPercentageChange() > 20) {
                    Suggestion s = new Suggestion(
                            "Optimize " + result.getCurrent().getTopCpuMethod(),
                            "Hot method consuming " + result.getCurrent().getTopCpuPercentage() + "% CPU (increased from " +
                                    result.getPrevious().getTopCpuPercentage() + "%). Consider caching, algorithm optimization, or parallelization.",
                            "CPU",
                            Suggestion.Complexity.MEDIUM,
                            Suggestion.Gain.HIGH
                    );
                    s.setRelatedMethod(result.getCurrent().getTopCpuMethod());
                    s.setEstimatedImprovementPercent(risk.getPercentageChange() / 2); // Conservative estimate
                    s.setImplementation(
                            "1. Profile the method to find bottlenecks\n" +
                                    "2. Check for repeated calculations that can be cached\n" +
                                    "3. Consider async execution if I/O bound\n" +
                                    "4. Review algorithm complexity (O(n²) → O(n log n))"
                    );
                    suggestions.add(s);
                }
            }
        }

        // Suggestions for memory risks
        for (ComparisonResult.Risk risk : result.getRisks()) {
            if ("MEMORY".equals(risk.getCategory())) {
                if (risk.getMetric().contains("allocation")) {
                    Suggestion s = new Suggestion(
                            "Reduce allocations in " + result.getCurrent().getTopAllocationMethod(),
                            "High allocation rate detected. Object pooling or reuse could reduce GC pressure.",
                            "MEMORY",
                            Suggestion.Complexity.LOW,
                            Suggestion.Gain.MEDIUM
                    );
                    s.setRelatedMethod(result.getCurrent().getTopAllocationMethod());
                    s.setEstimatedImprovementPercent(15);
                    s.setImplementation(
                            "1. Use object pools for frequently created objects\n" +
                                    "2. Reuse StringBuilder instances\n" +
                                    "3. Consider primitive arrays instead of boxed types\n" +
                                    "4. Use lazy initialization where possible"
                    );
                    suggestions.add(s);
                }
            }
        }

        // Suggestions for GC risks
        for (ComparisonResult.Risk risk : result.getRisks()) {
            if ("GC".equals(risk.getCategory()) && risk.getSeverity().equals("CRITICAL")) {
                Suggestion s = new Suggestion(
                        "Tune GC parameters",
                        "High GC pause times detected. Consider switching GC algorithm or adjusting heap size.",
                        "GC",
                        Suggestion.Complexity.TRIVIAL,
                        Suggestion.Gain.HIGH
                    );
                s.setEstimatedImprovementPercent(25);
                s.setImplementation(
                        "1. Try G1GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=200\n" +
                                "2. Or ZGC for low latency: -XX:+UseZGC\n" +
                                "3. Increase heap size: -Xmx4g -Xms4g\n" +
                                "4. Enable GC logging: -Xlog:gc*:file=gc.log"
                );
                suggestions.add(s);
            }
        }

        // Sort by priority score (highest first)
        suggestions.sort((a, b) -> Integer.compare(b.getPriorityScore(), a.getPriorityScore()));

        result.setSuggestions(suggestions);
    }

    /**
     * Calculate percentage change.
     */
    private double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) return newValue > 0 ? 100.0 : 0.0;
        return ((newValue - oldValue) / oldValue) * 100.0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
