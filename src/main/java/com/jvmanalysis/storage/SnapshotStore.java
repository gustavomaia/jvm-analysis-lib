package com.jvmanalysis.storage;

import com.jvmanalysis.model.AnalysisSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for storing and retrieving analysis snapshots.
 */
public interface SnapshotStore {

    /**
     * Save a snapshot asynchronously.
     *
     * @param snapshot Snapshot to save
     * @return CompletableFuture that completes when saved
     */
    CompletableFuture<Void> saveAsync(AnalysisSnapshot snapshot);

    /**
     * Get the most recent snapshot for a pod/region.
     *
     * @param podName Pod name (can be null for any pod in region)
     * @param region Region
     * @return CompletableFuture with optional snapshot
     */
    CompletableFuture<Optional<AnalysisSnapshot>> getLatestAsync(String podName, String region);

    /**
     * Get snapshot by ID.
     *
     * @param snapshotId Snapshot ID
     * @return CompletableFuture with optional snapshot
     */
    CompletableFuture<Optional<AnalysisSnapshot>> getByIdAsync(String snapshotId);

    /**
     * Get all snapshots for a pod within a time range.
     *
     * @param podName Pod name
     * @param region Region
     * @param limit Maximum number to return
     * @return CompletableFuture with list of snapshots
     */
    CompletableFuture<List<AnalysisSnapshot>> getHistoryAsync(String podName, String region, int limit);

    /**
     * Delete snapshots older than the specified days.
     *
     * @param daysOld Number of days
     * @return CompletableFuture with count of deleted snapshots
     */
    CompletableFuture<Integer> cleanupOldSnapshotsAsync(int daysOld);
}
