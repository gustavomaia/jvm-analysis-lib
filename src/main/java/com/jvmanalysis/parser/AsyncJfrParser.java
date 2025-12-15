package com.jvmanalysis.parser;

import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.JfrAnalysisData;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async JFR parser that processes CPU, memory, GC, and thread data in parallel.
 */
public class AsyncJfrParser {

    private static final Logger logger = LoggerFactory.getLogger(AsyncJfrParser.class);
    private final JvmAnalysisConfig.AnalysisConfig config;
    private final ExecutorService executor;
    private final JfrParser syncParser;

    public AsyncJfrParser(JvmAnalysisConfig.AnalysisConfig config) {
        this(config, Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("jfr-parser-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        ));
    }

    public AsyncJfrParser(JvmAnalysisConfig.AnalysisConfig config, ExecutorService executor) {
        this.config = config;
        this.executor = executor;
        this.syncParser = new JfrParser(config);
    }

    /**
     * Parse JFR file asynchronously with parallel extraction of different profiles.
     *
     * @param jfrFile Path to JFR file
     * @return CompletableFuture with analysis data
     */
    public CompletableFuture<JfrAnalysisData> parseAsync(Path jfrFile) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Loading JFR file asynchronously: {}", jfrFile);
            try {
                IItemCollection events = JfrLoaderToolkit.loadEvents(jfrFile.toFile());
                return events;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load JFR file: " + jfrFile, e);
            }
        }, executor).thenCompose(events -> parseEventsAsync(jfrFile, events));
    }

    /**
     * Parse events in parallel - CPU, Memory, GC, Threads all at once.
     *
     * @param jfrFile Path to original file
     * @param events Loaded JFR events
     * @return CompletableFuture with complete analysis data
     */
    private CompletableFuture<JfrAnalysisData> parseEventsAsync(Path jfrFile, IItemCollection events) {
        JfrAnalysisData data = new JfrAnalysisData();
        data.setJfrFilePath(jfrFile.toString());

        // Parse all profiles in parallel
        CompletableFuture<JfrAnalysisData.CpuProfile> cpuFuture = config.isAnalyzeCpu()
                ? CompletableFuture.supplyAsync(() -> syncParser.extractCpuProfile(events), executor)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<JfrAnalysisData.MemoryProfile> memoryFuture = config.isAnalyzeMemory()
                ? CompletableFuture.supplyAsync(() -> syncParser.extractMemoryProfile(events), executor)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<JfrAnalysisData.GcProfile> gcFuture = config.isAnalyzeGc()
                ? CompletableFuture.supplyAsync(() -> syncParser.extractGcProfile(events), executor)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<JfrAnalysisData.ThreadProfile> threadFuture = config.isAnalyzeThreads()
                ? CompletableFuture.supplyAsync(() -> syncParser.extractThreadProfile(events), executor)
                : CompletableFuture.completedFuture(null);

        // Wait for all to complete and combine results
        return CompletableFuture.allOf(cpuFuture, memoryFuture, gcFuture, threadFuture)
                .thenApply(v -> {
                    data.setCpuProfile(cpuFuture.join());
                    data.setMemoryProfile(memoryFuture.join());
                    data.setGcProfile(gcFuture.join());
                    data.setThreadProfile(threadFuture.join());
                    logger.info("Async JFR parsing completed for: {}", jfrFile);
                    return data;
                });
    }

    /**
     * Parse multiple JFR files in parallel.
     *
     * @param jfrFiles Paths to JFR files
     * @return CompletableFuture with list of analysis data
     */
    public CompletableFuture<java.util.List<JfrAnalysisData>> parseMultipleAsync(java.util.List<Path> jfrFiles) {
        java.util.List<CompletableFuture<JfrAnalysisData>> futures = jfrFiles.stream()
                .map(this::parseAsync)
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Make syncParser methods accessible for async execution.
     */
    JfrParser getSyncParser() {
        return syncParser;
    }
}
