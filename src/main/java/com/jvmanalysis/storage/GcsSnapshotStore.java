package com.jvmanalysis.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.storage.*;
import com.jvmanalysis.config.JvmAnalysisConfig;
import com.jvmanalysis.model.AnalysisSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;

/**
 * GCS-based snapshot storage for historical analysis comparison.
 * Stores snapshots as JSON files in GCS for easy versioning and querying.
 */
public class GcsSnapshotStore implements SnapshotStore {

    private static final Logger logger = LoggerFactory.getLogger(GcsSnapshotStore.class);
    private static final String SNAPSHOTS_PREFIX = "analysis-snapshots/";

    private final Storage storage;
    private final String bucketName;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public GcsSnapshotStore(Storage storage, String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.executor = Executors.newFixedThreadPool(4);
    }

    @Override
    public CompletableFuture<Void> saveAsync(AnalysisSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> {
            try {
                String path = buildSnapshotPath(snapshot);
                String json = objectMapper.writeValueAsString(snapshot);

                BlobId blobId = BlobId.of(bucketName, path);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("application/json")
                        .setMetadata(java.util.Map.of(
                                "pod", snapshot.getPodName() != null ? snapshot.getPodName() : "",
                                "region", snapshot.getRegion() != null ? snapshot.getRegion() : "",
                                "timestamp", String.valueOf(snapshot.getTimestamp().getEpochSecond()),
                                "version", snapshot.getDeploymentVersion() != null ? snapshot.getDeploymentVersion() : "",
                                "commit", snapshot.getGitCommitSha() != null ? snapshot.getGitCommitSha() : ""
                        ))
                        .build();

                storage.create(blobInfo, json.getBytes(StandardCharsets.UTF_8));
                logger.info("Saved snapshot: {}", path);

            } catch (IOException e) {
                throw new RuntimeException("Failed to save snapshot", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<AnalysisSnapshot>> getLatestAsync(String podName, String region) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prefix = SNAPSHOTS_PREFIX + region + "/";
                Page<Blob> blobs = storage.list(bucketName,
                        Storage.BlobListOption.prefix(prefix),
                        Storage.BlobListOption.currentDirectory());

                return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                        .filter(blob -> podName == null ||
                                blob.getMetadata().getOrDefault("pod", "").equals(podName))
                        .max(Comparator.comparing(blob ->
                                Long.parseLong(blob.getMetadata().getOrDefault("timestamp", "0"))))
                        .flatMap(blob -> {
                            try {
                                String json = new String(blob.getContent(), StandardCharsets.UTF_8);
                                return Optional.of(objectMapper.readValue(json, AnalysisSnapshot.class));
                            } catch (IOException e) {
                                logger.error("Failed to parse snapshot: {}", blob.getName(), e);
                                return Optional.empty();
                            }
                        });

            } catch (Exception e) {
                logger.error("Failed to get latest snapshot", e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<AnalysisSnapshot>> getByIdAsync(String snapshotId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Search for blob with matching ID in metadata
                Page<Blob> blobs = storage.list(bucketName,
                        Storage.BlobListOption.prefix(SNAPSHOTS_PREFIX));

                return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                        .filter(blob -> blob.getName().contains(snapshotId))
                        .findFirst()
                        .flatMap(blob -> {
                            try {
                                String json = new String(blob.getContent(), StandardCharsets.UTF_8);
                                return Optional.of(objectMapper.readValue(json, AnalysisSnapshot.class));
                            } catch (IOException e) {
                                logger.error("Failed to parse snapshot: {}", blob.getName(), e);
                                return Optional.empty();
                            }
                        });

            } catch (Exception e) {
                logger.error("Failed to get snapshot by ID: {}", snapshotId, e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AnalysisSnapshot>> getHistoryAsync(String podName, String region, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prefix = SNAPSHOTS_PREFIX + region + "/";
                Page<Blob> blobs = storage.list(bucketName,
                        Storage.BlobListOption.prefix(prefix));

                return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                        .filter(blob -> podName == null ||
                                blob.getMetadata().getOrDefault("pod", "").equals(podName))
                        .sorted(Comparator.comparing((Blob blob) ->
                                Long.parseLong(blob.getMetadata().getOrDefault("timestamp", "0"))).reversed())
                        .limit(limit)
                        .map(blob -> {
                            try {
                                String json = new String(blob.getContent(), StandardCharsets.UTF_8);
                                return objectMapper.readValue(json, AnalysisSnapshot.class);
                            } catch (IOException e) {
                                logger.error("Failed to parse snapshot: {}", blob.getName(), e);
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList());

            } catch (Exception e) {
                logger.error("Failed to get history", e);
                return new ArrayList<>();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> cleanupOldSnapshotsAsync(int daysOld) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Instant cutoff = Instant.now().minus(daysOld, ChronoUnit.DAYS);
                Page<Blob> blobs = storage.list(bucketName,
                        Storage.BlobListOption.prefix(SNAPSHOTS_PREFIX));

                int deleted = 0;
                for (Blob blob : blobs.iterateAll()) {
                    long timestamp = Long.parseLong(blob.getMetadata().getOrDefault("timestamp", "0"));
                    if (Instant.ofEpochSecond(timestamp).isBefore(cutoff)) {
                        blob.delete();
                        deleted++;
                    }
                }

                logger.info("Deleted {} snapshots older than {} days", deleted, daysOld);
                return deleted;

            } catch (Exception e) {
                logger.error("Failed to cleanup old snapshots", e);
                return 0;
            }
        }, executor);
    }

    /**
     * Build GCS path for snapshot.
     * Format: analysis-snapshots/{region}/{pod}/{timestamp}_{snapshotId}.json
     */
    private String buildSnapshotPath(AnalysisSnapshot snapshot) {
        String region = sanitize(snapshot.getRegion() != null ? snapshot.getRegion() : "unknown");
        String pod = sanitize(snapshot.getPodName() != null ? snapshot.getPodName() : "unknown");
        long timestamp = snapshot.getTimestamp().getEpochSecond();

        return String.format("%s%s/%s/%d_%s.json",
                SNAPSHOTS_PREFIX, region, pod, timestamp, snapshot.getSnapshotId());
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9-_.]", "_");
    }

    public void shutdown() {
        executor.shutdown();
    }
}
