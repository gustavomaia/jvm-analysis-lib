package com.jvmanalysis.parser;

import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.JfrAnalysisData;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses JFR dump files and extracts CPU, memory, GC, and thread data.
 */
public class JfrParser {

    private static final Logger logger = LoggerFactory.getLogger(JfrParser.class);
    private final JvmAnalysisConfig.AnalysisConfig config;

    public JfrParser(JvmAnalysisConfig.AnalysisConfig config) {
        this.config = config;
    }

    /**
     * Parse a JFR dump file and extract analysis data.
     *
     * @param jfrFile Path to JFR file
     * @return Parsed analysis data
     * @throws IOException if file cannot be read
     */
    public JfrAnalysisData parse(Path jfrFile) throws IOException {
        logger.info("Parsing JFR file: {}", jfrFile);

        IItemCollection events = JfrLoaderToolkit.loadEvents(jfrFile.toFile());

        JfrAnalysisData data = new JfrAnalysisData();
        data.setJfrFilePath(jfrFile.toString());

        if (config.isAnalyzeCpu()) {
            data.setCpuProfile(extractCpuProfile(events));
        }

        if (config.isAnalyzeMemory()) {
            data.setMemoryProfile(extractMemoryProfile(events));
        }

        if (config.isAnalyzeGc()) {
            data.setGcProfile(extractGcProfile(events));
        }

        if (config.isAnalyzeThreads()) {
            data.setThreadProfile(extractThreadProfile(events));
        }

        logger.info("JFR parsing completed");
        return data;
    }

    /**
     * Extract CPU profiling data from execution samples.
     */
    JfrAnalysisData.CpuProfile extractCpuProfile(IItemCollection events) {
        logger.info("Extracting CPU profile");

        JfrAnalysisData.CpuProfile profile = new JfrAnalysisData.CpuProfile();
        Map<String, MethodStats> methodStats = new HashMap<>();

        // Get execution sample events
        IItemCollection samples = events.apply(JdkTypeIDs.EXECUTION_SAMPLE);

        int sampleCount = 0;
        for (IItemIterable itemIterable : samples) {
            IMemberAccessor<IMCStackTrace, IItem> stackTraceAccessor =
                    JdkTypeIDs.EXECUTION_SAMPLE_STACKTRACE.getAccessor(itemIterable.getType());

            for (IItem item : itemIterable) {
                sampleCount++;
                IMCStackTrace stackTrace = stackTraceAccessor.getMember(item);
                if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
                    continue;
                }

                // Get top frame (actual executing method)
                IMCFrame topFrame = stackTrace.getFrames().get(0);
                IMCMethod method = topFrame.getMethod();

                String methodKey = getMethodKey(method);

                if (!shouldIncludeMethod(method)) {
                    continue;
                }

                MethodStats stats = methodStats.computeIfAbsent(methodKey, k -> new MethodStats(method));
                stats.samples++;
                stats.stackTrace = formatStackTrace(stackTrace);
            }
        }

        profile.setSampleCount(sampleCount);

        // Convert to sorted list of hot methods
        List<JfrAnalysisData.HotMethod> hotMethods = methodStats.values().stream()
                .sorted((a, b) -> Long.compare(b.samples, a.samples))
                .limit(config.getTopHotMethodsCount())
                .map(stats -> {
                    JfrAnalysisData.HotMethod hm = new JfrAnalysisData.HotMethod();
                    hm.setMethodName(stats.method.getMethodName());
                    hm.setClassName(stats.method.getType().getTypeName());
                    hm.setPackageName(stats.method.getType().getPackage() != null ?
                            stats.method.getType().getPackage().getName() : "");
                    hm.setSamples(stats.samples);
                    hm.setPercentage((stats.samples * 100.0) / sampleCount);
                    hm.setStackTrace(stats.stackTrace);
                    return hm;
                })
                .collect(Collectors.toList());

        profile.setHotMethods(hotMethods);

        logger.info("Found {} unique methods from {} samples", methodStats.size(), sampleCount);
        return profile;
    }

    /**
     * Extract memory allocation profile.
     */
    JfrAnalysisData.MemoryProfile extractMemoryProfile(IItemCollection events) {
        logger.info("Extracting memory profile");

        JfrAnalysisData.MemoryProfile profile = new JfrAnalysisData.MemoryProfile();
        Map<String, AllocationStats> allocationStats = new HashMap<>();

        // Get TLAB allocation events
        IItemCollection allocations = events.apply(JdkTypeIDs.ALLOC_INSIDE_TLAB);

        long totalAllocations = 0;
        long totalBytes = 0;

        for (IItemIterable itemIterable : allocations) {
            IMemberAccessor<IMCStackTrace, IItem> stackTraceAccessor =
                    JdkTypeIDs.ALLOC_INSIDE_TLAB_STACKTRACE.getAccessor(itemIterable.getType());
            IMemberAccessor<IQuantity, IItem> sizeAccessor =
                    JdkTypeIDs.ALLOC_INSIDE_TLAB_ALLOCATION_SIZE.getAccessor(itemIterable.getType());
            IMemberAccessor<?, IItem> classAccessor =
                    JdkTypeIDs.ALLOC_INSIDE_TLAB_OBJECT_CLASS.getAccessor(itemIterable.getType());

            for (IItem item : itemIterable) {
                totalAllocations++;

                IMCStackTrace stackTrace = stackTraceAccessor.getMember(item);
                IQuantity size = sizeAccessor.getMember(item);
                Object allocatedClass = classAccessor.getMember(item);

                if (stackTrace == null || stackTrace.getFrames().isEmpty() || size == null) {
                    continue;
                }

                long bytes = size.longValue();
                totalBytes += bytes;

                IMCFrame topFrame = stackTrace.getFrames().get(0);
                IMCMethod method = topFrame.getMethod();

                if (!shouldIncludeMethod(method)) {
                    continue;
                }

                String allocKey = getMethodKey(method) + ":" + allocatedClass;
                AllocationStats stats = allocationStats.computeIfAbsent(allocKey,
                        k -> new AllocationStats(method, allocatedClass != null ? allocatedClass.toString() : "Unknown"));
                stats.count++;
                stats.totalBytes += bytes;
                stats.stackTrace = formatStackTrace(stackTrace);
            }
        }

        profile.setTotalAllocations(totalAllocations);
        profile.setTotalBytesAllocated(totalBytes);

        // Convert to sorted list
        List<JfrAnalysisData.AllocationSite> topSites = allocationStats.values().stream()
                .sorted((a, b) -> Long.compare(b.totalBytes, a.totalBytes))
                .limit(config.getTopAllocationSitesCount())
                .map(stats -> {
                    JfrAnalysisData.AllocationSite site = new JfrAnalysisData.AllocationSite();
                    site.setMethodName(stats.method.getMethodName());
                    site.setClassName(stats.method.getType().getTypeName());
                    site.setAllocatedType(stats.allocatedType);
                    site.setAllocationCount(stats.count);
                    site.setTotalBytes(stats.totalBytes);
                    site.setStackTrace(stats.stackTrace);
                    return site;
                })
                .collect(Collectors.toList());

        profile.setTopAllocationSites(topSites);

        logger.info("Found {} allocations totaling {} MB",
                totalAllocations, totalBytes / (1024.0 * 1024.0));
        return profile;
    }

    /**
     * Extract GC statistics.
     */
    JfrAnalysisData.GcProfile extractGcProfile(IItemCollection events) {
        logger.info("Extracting GC profile");

        JfrAnalysisData.GcProfile profile = new JfrAnalysisData.GcProfile();

        // Get GC pause events
        IItemCollection gcPauses = events.apply(JdkTypeIDs.GC_PAUSE);

        long totalPauseTime = 0;
        long longestPause = 0;
        int gcCount = 0;

        for (IItemIterable itemIterable : gcPauses) {
            IMemberAccessor<IQuantity, IItem> durationAccessor =
                    JdkTypeIDs.GC_PAUSE_DURATION.getAccessor(itemIterable.getType());

            for (IItem item : itemIterable) {
                gcCount++;
                IQuantity duration = durationAccessor.getMember(item);
                if (duration != null) {
                    long pauseMs = duration.longValue() / 1_000_000; // Convert to ms
                    totalPauseTime += pauseMs;
                    longestPause = Math.max(longestPause, pauseMs);
                }
            }
        }

        profile.setGcCount(gcCount);
        profile.setTotalPauseTime(totalPauseTime);
        profile.setLongestPause(longestPause);

        // Identify issues
        List<String> issues = new ArrayList<>();
        if (longestPause > 100) {
            issues.add("Long GC pause detected: " + longestPause + "ms");
        }
        if (gcCount > 1000) {
            issues.add("High GC frequency: " + gcCount + " collections");
        }

        profile.setIssues(issues);

        logger.info("GC: {} collections, total pause: {}ms, longest: {}ms",
                gcCount, totalPauseTime, longestPause);
        return profile;
    }

    /**
     * Extract thread-related issues.
     */
    JfrAnalysisData.ThreadProfile extractThreadProfile(IItemCollection events) {
        logger.info("Extracting thread profile");

        JfrAnalysisData.ThreadProfile profile = new JfrAnalysisData.ThreadProfile();
        List<JfrAnalysisData.ThreadIssue> issues = new ArrayList<>();

        // Check for monitor blocked events
        IItemCollection blockedEvents = events.apply(JdkTypeIDs.MONITOR_WAIT);
        if (!blockedEvents.isEmpty()) {
            // Analyze contention
            int blockCount = 0;
            for (IItemIterable itemIterable : blockedEvents) {
                for (IItem item : itemIterable) {
                    blockCount++;
                }
            }

            if (blockCount > 100) {
                JfrAnalysisData.ThreadIssue issue = new JfrAnalysisData.ThreadIssue();
                issue.setType("CONTENTION");
                issue.setDescription("High monitor contention detected: " + blockCount + " wait events");
                issues.add(issue);
            }
        }

        profile.setIssues(issues);

        logger.info("Thread analysis completed, found {} issues", issues.size());
        return profile;
    }

    /**
     * Check if method should be included based on whitelist/blacklist.
     */
    private boolean shouldIncludeMethod(IMCMethod method) {
        if (method == null || method.getType() == null) {
            return false;
        }

        String packageName = method.getType().getPackage() != null ?
                method.getType().getPackage().getName() : "";
        String methodFullName = method.getType().getTypeName() + "." + method.getMethodName();

        // Check blacklists first
        if (!config.getMethodBlacklist().isEmpty()) {
            if (config.getMethodBlacklist().contains(methodFullName)) {
                return false;
            }
        }

        if (!config.getPackageBlacklist().isEmpty()) {
            for (String blacklistedPkg : config.getPackageBlacklist()) {
                if (packageName.startsWith(blacklistedPkg)) {
                    return false;
                }
            }
        }

        // Check whitelists
        if (!config.getPackageWhitelist().isEmpty()) {
            boolean inWhitelist = false;
            for (String whitelistedPkg : config.getPackageWhitelist()) {
                if (packageName.startsWith(whitelistedPkg)) {
                    inWhitelist = true;
                    break;
                }
            }
            if (!inWhitelist) {
                return false;
            }
        }

        if (!config.getMethodWhitelist().isEmpty()) {
            return config.getMethodWhitelist().contains(methodFullName);
        }

        return true;
    }

    private String getMethodKey(IMCMethod method) {
        return method.getType().getTypeName() + "." + method.getMethodName();
    }

    private String formatStackTrace(IMCStackTrace stackTrace) {
        if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (IMCFrame frame : stackTrace.getFrames()) {
            IMCMethod method = frame.getMethod();
            if (method != null) {
                sb.append("  at ")
                        .append(method.getType().getTypeName())
                        .append(".")
                        .append(method.getMethodName())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // Helper classes for aggregation
    private static class MethodStats {
        IMCMethod method;
        long samples = 0;
        String stackTrace;

        MethodStats(IMCMethod method) {
            this.method = method;
        }
    }

    private static class AllocationStats {
        IMCMethod method;
        String allocatedType;
        long count = 0;
        long totalBytes = 0;
        String stackTrace;

        AllocationStats(IMCMethod method, String allocatedType) {
            this.method = method;
            this.allocatedType = allocatedType;
        }
    }
}
