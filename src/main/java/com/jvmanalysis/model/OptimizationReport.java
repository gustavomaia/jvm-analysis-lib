package com.jvmanalysis.model;

import java.time.Instant;

/**
 * Optimization report containing JFR data and Claude's analysis.
 */
public class OptimizationReport {

    private JfrAnalysisData jfrData;
    private String claudeAnalysis;
    private Instant timestamp;
    private String podName;
    private String region;

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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
