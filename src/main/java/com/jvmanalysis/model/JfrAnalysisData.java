package com.jvmanalysis.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated data extracted from JFR dump for analysis.
 */
public class JfrAnalysisData {

    private String jfrFilePath;
    private String gcsPath;
    private Instant recordingStart;
    private Instant recordingEnd;
    private long durationMillis;

    private CpuProfile cpuProfile;
    private MemoryProfile memoryProfile;
    private GcProfile gcProfile;
    private ThreadProfile threadProfile;

    public String getJfrFilePath() {
        return jfrFilePath;
    }

    public void setJfrFilePath(String jfrFilePath) {
        this.jfrFilePath = jfrFilePath;
    }

    public String getGcsPath() {
        return gcsPath;
    }

    public void setGcsPath(String gcsPath) {
        this.gcsPath = gcsPath;
    }

    public Instant getRecordingStart() {
        return recordingStart;
    }

    public void setRecordingStart(Instant recordingStart) {
        this.recordingStart = recordingStart;
    }

    public Instant getRecordingEnd() {
        return recordingEnd;
    }

    public void setRecordingEnd(Instant recordingEnd) {
        this.recordingEnd = recordingEnd;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public CpuProfile getCpuProfile() {
        return cpuProfile;
    }

    public void setCpuProfile(CpuProfile cpuProfile) {
        this.cpuProfile = cpuProfile;
    }

    public MemoryProfile getMemoryProfile() {
        return memoryProfile;
    }

    public void setMemoryProfile(MemoryProfile memoryProfile) {
        this.memoryProfile = memoryProfile;
    }

    public GcProfile getGcProfile() {
        return gcProfile;
    }

    public void setGcProfile(GcProfile gcProfile) {
        this.gcProfile = gcProfile;
    }

    public ThreadProfile getThreadProfile() {
        return threadProfile;
    }

    public void setThreadProfile(ThreadProfile threadProfile) {
        this.threadProfile = threadProfile;
    }

    public static class CpuProfile {
        private List<HotMethod> hotMethods = new ArrayList<>();
        private double totalCpuTime;
        private int sampleCount;

        public List<HotMethod> getHotMethods() {
            return hotMethods;
        }

        public void setHotMethods(List<HotMethod> hotMethods) {
            this.hotMethods = hotMethods;
        }

        public double getTotalCpuTime() {
            return totalCpuTime;
        }

        public void setTotalCpuTime(double totalCpuTime) {
            this.totalCpuTime = totalCpuTime;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        public void setSampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
        }
    }

    public static class HotMethod {
        private String methodName;
        private String className;
        private String packageName;
        private long samples;
        private double percentage;
        private String stackTrace;

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public long getSamples() {
            return samples;
        }

        public void setSamples(long samples) {
            this.samples = samples;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        @Override
        public String toString() {
            return String.format("%s.%s: %.2f%% (%d samples)", className, methodName, percentage, samples);
        }
    }

    public static class MemoryProfile {
        private List<AllocationSite> topAllocationSites = new ArrayList<>();
        private long totalAllocations;
        private long totalBytesAllocated;

        public List<AllocationSite> getTopAllocationSites() {
            return topAllocationSites;
        }

        public void setTopAllocationSites(List<AllocationSite> topAllocationSites) {
            this.topAllocationSites = topAllocationSites;
        }

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
    }

    public static class AllocationSite {
        private String methodName;
        private String className;
        private String allocatedType;
        private long allocationCount;
        private long totalBytes;
        private String stackTrace;

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getAllocatedType() {
            return allocatedType;
        }

        public void setAllocatedType(String allocatedType) {
            this.allocatedType = allocatedType;
        }

        public long getAllocationCount() {
            return allocationCount;
        }

        public void setAllocationCount(long allocationCount) {
            this.allocationCount = allocationCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public void setTotalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        @Override
        public String toString() {
            return String.format("%s in %s.%s: %d allocations, %.2f MB",
                    allocatedType, className, methodName, allocationCount, totalBytes / (1024.0 * 1024.0));
        }
    }

    public static class GcProfile {
        private long totalPauseTime;
        private long longestPause;
        private int gcCount;
        private String gcType;
        private List<String> issues = new ArrayList<>();

        public long getTotalPauseTime() {
            return totalPauseTime;
        }

        public void setTotalPauseTime(long totalPauseTime) {
            this.totalPauseTime = totalPauseTime;
        }

        public long getLongestPause() {
            return longestPause;
        }

        public void setLongestPause(long longestPause) {
            this.longestPause = longestPause;
        }

        public int getGcCount() {
            return gcCount;
        }

        public void setGcCount(int gcCount) {
            this.gcCount = gcCount;
        }

        public String getGcType() {
            return gcType;
        }

        public void setGcType(String gcType) {
            this.gcType = gcType;
        }

        public List<String> getIssues() {
            return issues;
        }

        public void setIssues(List<String> issues) {
            this.issues = issues;
        }
    }

    public static class ThreadProfile {
        private int maxThreadCount;
        private List<ThreadIssue> issues = new ArrayList<>();

        public int getMaxThreadCount() {
            return maxThreadCount;
        }

        public void setMaxThreadCount(int maxThreadCount) {
            this.maxThreadCount = maxThreadCount;
        }

        public List<ThreadIssue> getIssues() {
            return issues;
        }

        public void setIssues(List<ThreadIssue> issues) {
            this.issues = issues;
        }
    }

    public static class ThreadIssue {
        private String type; // "DEADLOCK", "CONTENTION", "BLOCKED"
        private String description;
        private List<String> threadsInvolved = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getThreadsInvolved() {
            return threadsInvolved;
        }

        public void setThreadsInvolved(List<String> threadsInvolved) {
            this.threadsInvolved = threadsInvolved;
        }
    }
}
