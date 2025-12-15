package com.jvmanalysis.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for JVM analysis automation.
 */
public class JvmAnalysisConfig {

    private ClaudeConfig claude;
    private SlackConfig slack;
    private GcsConfig gcs;
    private JfrConfig jfr;
    private AnalysisConfig analysis;

    public ClaudeConfig getClaude() {
        return claude;
    }

    public void setClaude(ClaudeConfig claude) {
        this.claude = claude;
    }

    public SlackConfig getSlack() {
        return slack;
    }

    public void setSlack(SlackConfig slack) {
        this.slack = slack;
    }

    public GcsConfig getGcs() {
        return gcs;
    }

    public void setGcs(GcsConfig gcs) {
        this.gcs = gcs;
    }

    public JfrConfig getJfr() {
        return jfr;
    }

    public void setJfr(JfrConfig jfr) {
        this.jfr = jfr;
    }

    public AnalysisConfig getAnalysis() {
        return analysis;
    }

    public void setAnalysis(AnalysisConfig analysis) {
        this.analysis = analysis;
    }

    public static class ClaudeConfig {
        private String apiKey;
        private String model = "claude-sonnet-4-5-20250929";
        private int maxTokens = 4096;
        private String apiUrl = "https://api.anthropic.com/v1/messages";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }
    }

    public static class SlackConfig {
        private String webhookUrl;
        private String channel;
        private boolean enabled = true;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class GcsConfig {
        private String bucketName;
        private String credentialsPath;
        private String projectId;
        private boolean enabled = true;

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getCredentialsPath() {
            return credentialsPath;
        }

        public void setCredentialsPath(String credentialsPath) {
            this.credentialsPath = credentialsPath;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class JfrConfig {
        private Duration duration = Duration.ofMinutes(1);
        private String settings = "profile"; // or "default"
        private boolean compressOnUpload = true;
        private String localStoragePath = "/tmp/jfr-dumps";

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }

        public String getSettings() {
            return settings;
        }

        public void setSettings(String settings) {
            this.settings = settings;
        }

        public boolean isCompressOnUpload() {
            return compressOnUpload;
        }

        public void setCompressOnUpload(boolean compressOnUpload) {
            this.compressOnUpload = compressOnUpload;
        }

        public String getLocalStoragePath() {
            return localStoragePath;
        }

        public void setLocalStoragePath(String localStoragePath) {
            this.localStoragePath = localStoragePath;
        }
    }

    public static class AnalysisConfig {
        private Set<String> methodWhitelist = new HashSet<>();
        private Set<String> methodBlacklist = new HashSet<>();
        private Set<String> packageWhitelist = new HashSet<>();
        private Set<String> packageBlacklist = new HashSet<>();
        private boolean analyzeThreads = true;
        private boolean analyzeCpu = true;
        private boolean analyzeMemory = true;
        private boolean analyzeGc = true;
        private int topHotMethodsCount = 20;
        private int topAllocationSitesCount = 20;

        public Set<String> getMethodWhitelist() {
            return methodWhitelist;
        }

        public void setMethodWhitelist(Set<String> methodWhitelist) {
            this.methodWhitelist = methodWhitelist;
        }

        public Set<String> getMethodBlacklist() {
            return methodBlacklist;
        }

        public void setMethodBlacklist(Set<String> methodBlacklist) {
            this.methodBlacklist = methodBlacklist;
        }

        public Set<String> getPackageWhitelist() {
            return packageWhitelist;
        }

        public void setPackageWhitelist(Set<String> packageWhitelist) {
            this.packageWhitelist = packageWhitelist;
        }

        public Set<String> getPackageBlacklist() {
            return packageBlacklist;
        }

        public void setPackageBlacklist(Set<String> packageBlacklist) {
            this.packageBlacklist = packageBlacklist;
        }

        public boolean isAnalyzeThreads() {
            return analyzeThreads;
        }

        public void setAnalyzeThreads(boolean analyzeThreads) {
            this.analyzeThreads = analyzeThreads;
        }

        public boolean isAnalyzeCpu() {
            return analyzeCpu;
        }

        public void setAnalyzeCpu(boolean analyzeCpu) {
            this.analyzeCpu = analyzeCpu;
        }

        public boolean isAnalyzeMemory() {
            return analyzeMemory;
        }

        public void setAnalyzeMemory(boolean analyzeMemory) {
            this.analyzeMemory = analyzeMemory;
        }

        public boolean isAnalyzeGc() {
            return analyzeGc;
        }

        public void setAnalyzeGc(boolean analyzeGc) {
            this.analyzeGc = analyzeGc;
        }

        public int getTopHotMethodsCount() {
            return topHotMethodsCount;
        }

        public void setTopHotMethodsCount(int topHotMethodsCount) {
            this.topHotMethodsCount = topHotMethodsCount;
        }

        public int getTopAllocationSitesCount() {
            return topAllocationSitesCount;
        }

        public void setTopAllocationSitesCount(int topAllocationSitesCount) {
            this.topAllocationSitesCount = topAllocationSitesCount;
        }
    }
}
