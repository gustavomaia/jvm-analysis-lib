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
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

/**
 * Uploads JFR dumps and analysis reports to Google Cloud Storage.
 */
public class GcsUploader {

    private static final Logger logger = LoggerFactory.getLogger(GcsUploader.class);
    private final Storage storage;
    private final JvmAnalysisConfig.GcsConfig config;

    public GcsUploader(JvmAnalysisConfig.GcsConfig config) throws IOException {
        this.config = config;

        if (!config.isEnabled()) {
            this.storage = null;
            logger.info("GCS uploader is disabled");
            return;
        }

        // Initialize GCS client
        GoogleCredentials credentials;
        if (config.getCredentialsPath() != null) {
            try (FileInputStream credentialsStream = new FileInputStream(config.getCredentialsPath())) {
                credentials = GoogleCredentials.fromStream(credentialsStream);
            }
        } else {
            // Use application default credentials (from GKE metadata service)
            credentials = GoogleCredentials.getApplicationDefault();
        }

        this.storage = StorageOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .setCredentials(credentials)
                .build()
                .getService();

        logger.info("GCS uploader initialized for bucket: {}", config.getBucketName());
    }

    /**
     * Upload JFR dump file asynchronously.
     *
     * @param jfrFile Path to JFR file
     * @param region Region name for organization
     * @param podName Pod name for identification
     * @return CompletableFuture with GCS path
     */
    public CompletableFuture<String> uploadJfrDumpAsync(Path jfrFile, String region, String podName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return uploadJfrDump(jfrFile, region, podName);
            } catch (IOException e) {
                logger.error("Failed to upload JFR dump to GCS", e);
                throw new RuntimeException("GCS upload failed", e);
            }
        });
    }

    /**
     * Upload JFR dump file to GCS.
     *
     * @param jfrFile Path to JFR file
     * @param region Region name
     * @param podName Pod name
     * @return GCS path (gs://bucket/path)
     * @throws IOException if upload fails
     */
    public String uploadJfrDump(Path jfrFile, String region, String podName) throws IOException {
        if (!config.isEnabled()) {
            logger.info("GCS disabled, skipping upload");
            return jfrFile.toString();
        }

        long timestamp = System.currentTimeMillis();
        String fileName = jfrFile.getFileName().toString();

        // Organize by: region/date/pod/filename
        String datePath = String.format("%tY/%tm/%td", timestamp, timestamp, timestamp);
        String gcsPath = String.format("jfr-dumps/%s/%s/%s/%s", region, datePath, podName, fileName);

        byte[] content = Files.readAllBytes(jfrFile);

        // Optionally compress
        if (config.isCompressOnUpload()) {
            content = compress(content);
            gcsPath += ".gz";
        }

        BlobId blobId = BlobId.of(config.getBucketName(), gcsPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/octet-stream")
                .setMetadata(java.util.Map.of(
                        "region", region,
                        "pod", podName,
                        "timestamp", String.valueOf(timestamp)
                ))
                .build();

        storage.create(blobInfo, content);

        String gcsUrl = String.format("gs://%s/%s", config.getBucketName(), gcsPath);
        logger.info("JFR dump uploaded to: {}", gcsUrl);

        return gcsUrl;
    }

    /**
     * Upload analysis report asynchronously.
     *
     * @param reportContent Report content (markdown/text)
     * @param region Region name
     * @param podName Pod name
     * @return CompletableFuture with GCS path
     */
    public CompletableFuture<String> uploadReportAsync(String reportContent, String region, String podName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return uploadReport(reportContent, region, podName);
            } catch (IOException e) {
                logger.error("Failed to upload report to GCS", e);
                throw new RuntimeException("GCS upload failed", e);
            }
        });
    }

    /**
     * Upload analysis report to GCS.
     *
     * @param reportContent Report content
     * @param region Region name
     * @param podName Pod name
     * @return GCS path
     * @throws IOException if upload fails
     */
    public String uploadReport(String reportContent, String region, String podName) throws IOException {
        if (!config.isEnabled()) {
            logger.info("GCS disabled, skipping report upload");
            return "local-report.md";
        }

        long timestamp = System.currentTimeMillis();
        String datePath = String.format("%tY/%tm/%td", timestamp, timestamp, timestamp);
        String gcsPath = String.format("analysis-reports/%s/%s/%s/report-%d.md",
                region, datePath, podName, timestamp);

        BlobId blobId = BlobId.of(config.getBucketName(), gcsPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("text/markdown")
                .setMetadata(java.util.Map.of(
                        "region", region,
                        "pod", podName,
                        "timestamp", String.valueOf(timestamp)
                ))
                .build();

        storage.create(blobInfo, reportContent.getBytes());

        String gcsUrl = String.format("gs://%s/%s", config.getBucketName(), gcsPath);
        logger.info("Report uploaded to: {}", gcsUrl);

        return gcsUrl;
    }

    /**
     * Compress content with GZIP.
     */
    private byte[] compress(byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(content);
        }
        byte[] compressed = baos.toByteArray();
        logger.info("Compressed {} bytes to {} bytes", content.length, compressed.length);
        return compressed;
    }
}
