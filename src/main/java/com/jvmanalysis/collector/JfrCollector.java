package com.jvmanalysis.collector;

import com.jvmanalysis.config.JvmAnalysisConfig;
import jdk.jfr.Recording;
import jdk.jfr.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages JFR recording lifecycle - start, stop, and dump collection.
 */
public class JfrCollector {

    private static final Logger logger = LoggerFactory.getLogger(JfrCollector.class);
    private final JvmAnalysisConfig.JfrConfig config;
    private final AtomicReference<Recording> activeRecording = new AtomicReference<>();

    public JfrCollector(JvmAnalysisConfig.JfrConfig config) {
        this.config = config;
    }

    /**
     * Start a new JFR recording.
     *
     * @return Recording instance
     * @throws IOException if configuration cannot be loaded
     * @throws ParseException if configuration is invalid
     */
    public Recording startRecording() throws IOException, ParseException {
        Recording existing = activeRecording.get();
        if (existing != null && existing.getState() == Recording.State.RUNNING) {
            logger.warn("Recording already in progress, stopping it first");
            stopRecording();
        }

        Configuration configuration = Configuration.getConfiguration(config.getSettings());
        Recording recording = new Recording(configuration);

        recording.setName("jvm-analysis-" + Instant.now().getEpochSecond());
        recording.setMaxAge(config.getDuration().multipliedBy(2)); // Keep data for 2x duration
        recording.setDumpOnExit(false);

        recording.start();
        activeRecording.set(recording);

        logger.info("JFR recording started: {} with settings: {}", recording.getName(), config.getSettings());
        return recording;
    }

    /**
     * Start recording for a specific duration and automatically dump.
     *
     * @return Path to the dumped JFR file
     * @throws Exception if recording fails
     */
    public Path recordAndDump() throws Exception {
        Recording recording = startRecording();

        logger.info("Recording for {} ms", config.getDuration().toMillis());
        Thread.sleep(config.getDuration().toMillis());

        return stopRecording();
    }

    /**
     * Stop the active recording and dump to file.
     *
     * @return Path to the dumped JFR file
     * @throws IOException if dump fails
     */
    public Path stopRecording() throws IOException {
        Recording recording = activeRecording.getAndSet(null);
        if (recording == null) {
            throw new IllegalStateException("No active recording to stop");
        }

        // Create dump directory if it doesn't exist
        Path dumpDir = Paths.get(config.getLocalStoragePath());
        Files.createDirectories(dumpDir);

        // Generate filename with timestamp
        String filename = String.format("jfr-dump-%s-%d.jfr",
                recording.getName(),
                System.currentTimeMillis());
        Path dumpPath = dumpDir.resolve(filename);

        // Dump and close
        recording.dump(dumpPath);
        recording.close();

        logger.info("JFR recording stopped and dumped to: {}", dumpPath);
        return dumpPath;
    }

    /**
     * Get the current recording state.
     *
     * @return true if a recording is active
     */
    public boolean isRecording() {
        Recording recording = activeRecording.get();
        return recording != null && recording.getState() == Recording.State.RUNNING;
    }

    /**
     * Emergency stop - closes recording without dump.
     */
    public void emergencyStop() {
        Recording recording = activeRecording.getAndSet(null);
        if (recording != null) {
            try {
                recording.close();
                logger.info("Emergency stop - recording closed without dump");
            } catch (Exception e) {
                logger.error("Error during emergency stop", e);
            }
        }
    }
}
