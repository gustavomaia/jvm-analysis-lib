package com.jvmanalysis.model;

import java.time.Instant;

/**
 * Structured snapshot of JVM analysis results for historical comparison.
 * This template makes it easy to detect performance changes between deployments.
 */
public class AnalysisSnapshot {

    // Metadata
    private String snapshotId;
    private Instant timestamp;
    private String podName;
    private String region;
    private String deploymentVersion;
    private String gitCommitSha;

    // CPU Metrics
    private CpuMetrics cpu;

    // Memory Metrics
    private MemoryMetrics memory;

    // GC Metrics
    private GcMetrics gc;

    // Thread Metrics
    private ThreadMetrics threads;

    // Top Issues (for quick comparison)
    private String topCpuMethod;
    private double topCpuPercentage;
    private String topAllocationMethod;
    private long topAllocationBytes;

    // Getters and setters
    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
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

    public String getDeploymentVersion() {
        return deploymentVersion;
    }

    public void setDeploymentVersion(String deploymentVersion) {
        this.deploymentVersion = deploymentVersion;
    }

    public String getGitCommitSha() {
        return gitCommitSha;
    }

    public void setGitCommitSha(String gitCommitSha) {
        this.gitCommitSha = gitCommitSha;
    }

    public CpuMetrics getCpu() {
        return cpu;
    }

    public void setCpu(CpuMetrics cpu) {
        this.cpu = cpu;
    }

    public MemoryMetrics getMemory() {
        return memory;
    }

    public void setMemory(MemoryMetrics memory) {
        this.memory = memory;
    }

    public GcMetrics getGc() {
        return gc;
    }

    public void setGc(GcMetrics gc) {
        this.gc = gc;
    }

    public ThreadMetrics getThreads() {
        return threads;
    }

    public void setThreads(ThreadMetrics threads) {
        this.threads = threads;
    }

    public String getTopCpuMethod() {
        return topCpuMethod;
    }

    public void setTopCpuMethod(String topCpuMethod) {
        this.topCpuMethod = topCpuMethod;
    }

    public double getTopCpuPercentage() {
        return topCpuPercentage;
    }

    public void setTopCpuPercentage(double topCpuPercentage) {
        this.topCpuPercentage = topCpuPercentage;
    }

    public String getTopAllocationMethod() {
        return topAllocationMethod;
    }

    public void setTopAllocationMethod(String topAllocationMethod) {
        this.topAllocationMethod = topAllocationMethod;
    }

    public long getTopAllocationBytes() {
        return topAllocationBytes;
    }

    public void setTopAllocationBytes(long topAllocationBytes) {
        this.topAllocationBytes = topAllocationBytes;
    }

    /**
     * CPU metrics for comparison.
     */
    public static class CpuMetrics {
        private int totalSamples;
        private int uniqueHotMethods;
        private double avgCpuPerMethod;
        private int methodsAbove5Percent;

        public int getTotalSamples() {
            return totalSamples;
        }

        public void setTotalSamples(int totalSamples) {
            this.totalSamples = totalSamples;
        }

        public int getUniqueHotMethods() {
            return uniqueHotMethods;
        }

        public void setUniqueHotMethods(int uniqueHotMethods) {
            this.uniqueHotMethods = uniqueHotMethods;
        }

        public double getAvgCpuPerMethod() {
            return avgCpuPerMethod;
        }

        public void setAvgCpuPerMethod(double avgCpuPerMethod) {
            this.avgCpuPerMethod = avgCpuPerMethod;
        }

        public int getMethodsAbove5Percent() {
            return methodsAbove5Percent;
        }

        public void setMethodsAbove5Percent(int methodsAbove5Percent) {
            this.methodsAbove5Percent = methodsAbove5Percent;
        }
    }

    /**
     * Memory metrics for comparison.
     */
    public static class MemoryMetrics {
        private long totalAllocations;
        private long totalBytesAllocated;
        private long avgAllocationSize;
        private int uniqueAllocationSites;

        public long getTotalAllocations() {
            return totalAllocations;
        }

        public void setTotalAllocations(long totalAllocations) {
            this.totalAllocations = totalAllocations;
        }

        public long getTotalBytesAllocated() {
            return totalBytesAllocated;
        }

        public void setTotalBytesAllocated(long totalBytesAllocated) {
            this.totalBytesAllocated = totalBytesAllocated;
        }

        public long getAvgAllocationSize() {
            return avgAllocationSize;
        }

        public void setAvgAllocationSize(long avgAllocationSize) {
            this.avgAllocationSize = avgAllocationSize;
        }

        public int getUniqueAllocationSites() {
            return uniqueAllocationSites;
        }

        public void setUniqueAllocationSites(int uniqueAllocationSites) {
            this.uniqueAllocationSites = uniqueAllocationSites;
        }
    }

    /**
     * GC metrics for comparison.
     */
    public static class GcMetrics {
        private int gcCount;
        private long totalPauseTimeMs;
        private long longestPauseMs;
        private double avgPauseMs;
        private long pausesAbove100Ms;

        public int getGcCount() {
            return gcCount;
        }

        public void setGcCount(int gcCount) {
            this.gcCount = gcCount;
        }

        public long getTotalPauseTimeMs() {
            return totalPauseTimeMs;
        }

        public void setTotalPauseTimeMs(long totalPauseTimeMs) {
            this.totalPauseTimeMs = totalPauseTimeMs;
        }

        public long getLongestPauseMs() {
            return longestPauseMs;
        }

        public void setLongestPauseMs(long longestPauseMs) {
            this.longestPauseMs = longestPauseMs;
        }

        public double getAvgPauseMs() {
            return avgPauseMs;
        }

        public void setAvgPauseMs(double avgPauseMs) {
            this.avgPauseMs = avgPauseMs;
        }

        public long getPausesAbove100Ms() {
            return pausesAbove100Ms;
        }

        public void setPausesAbove100Ms(long pausesAbove100Ms) {
            this.pausesAbove100Ms = pausesAbove100Ms;
        }
    }

    /**
     * Thread metrics for comparison.
     */
    public static class ThreadMetrics {
        private int maxThreadCount;
        private int contentionEvents;
        private int deadlocksDetected;

        public int getMaxThreadCount() {
            return maxThreadCount;
        }

        public void setMaxThreadCount(int maxThreadCount) {
            this.maxThreadCount = maxThreadCount;
        }

        public int getContentionEvents() {
            return contentionEvents;
        }

        public void setContentionEvents(int contentionEvents) {
            this.contentionEvents = contentionEvents;
        }

        public int getDeadlocksDetected() {
            return deadlocksDetected;
        }

        public void setDeadlocksDetected(int deadlocksDetected) {
            this.deadlocksDetected = deadlocksDetected;
        }
    }

    /**
     * Create snapshot from JFR analysis data.
     */
    public static AnalysisSnapshot fromJfrData(JfrAnalysisData data) {
        AnalysisSnapshot snapshot = new AnalysisSnapshot();
        snapshot.setSnapshotId(java.util.UUID.randomUUID().toString());
        snapshot.setTimestamp(Instant.now());
        snapshot.setGitCommitSha(System.getenv("GIT_COMMIT_SHA"));
        snapshot.setDeploymentVersion(System.getenv("DEPLOYMENT_VERSION"));

        // CPU metrics
        if (data.getCpuProfile() != null) {
            CpuMetrics cpu = new CpuMetrics();
            cpu.setTotalSamples(data.getCpuProfile().getSampleCount());
            cpu.setUniqueHotMethods(data.getCpuProfile().getHotMethods().size());

            long methodsAbove5 = data.getCpuProfile().getHotMethods().stream()
                    .filter(m -> m.getPercentage() >= 5.0)
                    .count();
            cpu.setMethodsAbove5Percent((int) methodsAbove5);

            double avgCpu = data.getCpuProfile().getHotMethods().stream()
                    .mapToDouble(JfrAnalysisData.HotMethod::getPercentage)
                    .average()
                    .orElse(0.0);
            cpu.setAvgCpuPerMethod(avgCpu);

            snapshot.setCpu(cpu);

            // Top CPU method
            if (!data.getCpuProfile().getHotMethods().isEmpty()) {
                JfrAnalysisData.HotMethod top = data.getCpuProfile().getHotMethods().get(0);
                snapshot.setTopCpuMethod(top.getClassName() + "." + top.getMethodName());
                snapshot.setTopCpuPercentage(top.getPercentage());
            }
        }

        // Memory metrics
        if (data.getMemoryProfile() != null) {
            MemoryMetrics memory = new MemoryMetrics();
            memory.setTotalAllocations(data.getMemoryProfile().getTotalAllocations());
            memory.setTotalBytesAllocated(data.getMemoryProfile().getTotalBytesAllocated());
            memory.setUniqueAllocationSites(data.getMemoryProfile().getTopAllocationSites().size());

            if (data.getMemoryProfile().getTotalAllocations() > 0) {
                memory.setAvgAllocationSize(
                        data.getMemoryProfile().getTotalBytesAllocated() /
                                data.getMemoryProfile().getTotalAllocations()
                );
            }

            snapshot.setMemory(memory);

            // Top allocation method
            if (!data.getMemoryProfile().getTopAllocationSites().isEmpty()) {
                JfrAnalysisData.AllocationSite top = data.getMemoryProfile().getTopAllocationSites().get(0);
                snapshot.setTopAllocationMethod(top.getClassName() + "." + top.getMethodName());
                snapshot.setTopAllocationBytes(top.getTotalBytes());
            }
        }

        // GC metrics
        if (data.getGcProfile() != null) {
            GcMetrics gc = new GcMetrics();
            gc.setGcCount(data.getGcProfile().getGcCount());
            gc.setTotalPauseTimeMs(data.getGcProfile().getTotalPauseTime());
            gc.setLongestPauseMs(data.getGcProfile().getLongestPause());

            if (data.getGcProfile().getGcCount() > 0) {
                gc.setAvgPauseMs((double) data.getGcProfile().getTotalPauseTime() /
                        data.getGcProfile().getGcCount());
            }

            // Count pauses > 100ms (would need to track this in JfrParser)
            gc.setPausesAbove100Ms(data.getGcProfile().getLongestPause() > 100 ? 1 : 0);

            snapshot.setGc(gc);
        }

        // Thread metrics
        if (data.getThreadProfile() != null) {
            ThreadMetrics threads = new ThreadMetrics();
            threads.setMaxThreadCount(data.getThreadProfile().getMaxThreadCount());
            threads.setContentionEvents(data.getThreadProfile().getIssues().size());
            threads.setDeadlocksDetected((int) data.getThreadProfile().getIssues().stream()
                    .filter(i -> "DEADLOCK".equals(i.getType()))
                    .count());

            snapshot.setThreads(threads);
        }

        return snapshot;
    }
}
