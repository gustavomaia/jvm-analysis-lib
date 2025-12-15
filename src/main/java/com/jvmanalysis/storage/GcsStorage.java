package com.jvmanalysis.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.jvmanalysis.config.JvmAnalysisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Async Google Cloud Storage integration for uploading JFR dumps.
 */
public class GcsStorage {

    private static final Logger logger = LoggerFactory.getLogger(GcsStorage.class);
    private final JvmAnalysisConfig.GcsConfig config;
    private final Storage storage;
    private final ExecutorService executor;

    public GcsStorage(JvmAnalysisConfig.GcsConfig config) throws IOException {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r);
            t.setName("gcs-uploader-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        if (config.isEnabled()) {
            GoogleCredentials credentials;
            if (config.getCredentialsPath() != null && !config.getCredentialsPath().isEmpty()) {
                credentials = GoogleCredentials.fromStream(new FileInputStream(config.getCredentialsPath()));
            } else {
                // Use application default credentials
                credentials = GoogleCredentials.getApplicationDefault();
            }

            this.storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(config.getProjectId())
                    .build()
                    .getService();

            logger.info("GCS Storage initialized for bucket: {}", config.getBucketName());
        } else {
            this.storage = null;
            logger.info("GCS Storage disabled");
        }
    }

    /**
     * Upload JFR file to GCS asynchronously.
     *
     * @param localFile Path to local JFR file
     * @param remotePath Remote path in GCS bucket (without gs:// prefix)
     * @return CompletableFuture with GCS URI
     */
    public CompletableFuture<String> uploadAsync(Path localFile, String remotePath) {
        if (!config.isEnabled()) {
            logger.warn("GCS upload skipped - storage disabled");
            return CompletableFuture.completedFuture("file://" + localFile.toString());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Uploading {} to GCS: {}", localFile.getFileName(), remotePath);

                BlobId blobId = BlobId.of(config.getBucketName(), remotePath);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("application/octet-stream")
                        .build();

                Path fileToUpload = localFile;

                // Compress if enabled
                if (config.isCompressOnUpload() && !remotePath.endsWith(".gz")) {
                    fileToUpload = compressFile(localFile);
                    blobId = BlobId.of(config.getBucketName(), remotePath + ".gz");
                    blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType("application/gzip")
                            .build();
                }

                byte[] fileBytes = Files.readAllBytes(fileToUpload);
                Blob blob = storage.create(blobInfo, fileBytes);

                String gcsUri = String.format("gs://%s/%s", config.getBucketName(), blob.getName());
                logger.info("Upload complete: {} ({} bytes)", gcsUri, fileBytes.length);

                // Clean up compressed temp file
                if (config.isCompressOnUpload() && !fileToUpload.equals(localFile)) {
                    Files.deleteIfExists(fileToUpload);
                }

                return gcsUri;

            } catch (IOException e) {
                logger.error("Failed to upload to GCS: {}", remotePath, e);
                throw new RuntimeException("GCS upload failed", e);
            }
        }, executor);
    }

    /**
     * Compress file with GZIP.
     */
    private Path compressFile(Path input) throws IOException {
        Path compressed = input.getParent().resolve(input.getFileName() + ".gz");

        try (var fis = Files.newInputStream(input);
             var fos = Files.newOutputStream(compressed);
             var gzipOut = new GZIPOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzipOut.write(buffer, 0, len);
            }
        }

        logger.info("Compressed {} -> {} ({}% reduction)",
                input.getFileName(),
                compressed.getFileName(),
                (int) ((1.0 - (double) Files.size(compressed) / Files.size(input)) * 100));

        return compressed;
    }

    /**
     * Generate remote path for JFR file based on timestamp and pod info.
     *
     * @param podName Pod name or hostname
     * @param timestamp Timestamp string
     * @return Remote path in format: jfr-dumps/{pod}/{timestamp}.jfr
     */
    public String generateRemotePath(String podName, String timestamp) {
        return String.format("jfr-dumps/%s/%s.jfr", sanitize(podName), timestamp);
    }

    /**
     * Sanitize string for use in GCS path.
     */
    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9-_.]", "_");
    }

    /**
     * Shutdown executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
