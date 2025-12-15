package com.jvmanalysis.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for determining if this pod should collect JFR dumps.
 * Useful for multi-region Kubernetes deployments where you want
 * only one pod per region to collect dumps.
 */
public class PodSelectionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PodSelectionStrategy.class);

    /**
     * Check if this pod should collect JFR dumps based on multiple strategies.
     *
     * @return true if this pod should collect
     */
    public boolean shouldCollect() {
        // Strategy 1: Explicit environment flag
        if (checkExplicitFlag()) {
            logger.info("Pod selected for JFR collection via explicit flag");
            return true;
        }

        // Strategy 2: StatefulSet pod-0 selection
        if (checkStatefulSetPod0()) {
            logger.info("Pod selected for JFR collection as StatefulSet pod-0");
            return true;
        }

        // Strategy 3: Deployment with specific pod name pattern
        if (checkPodNamePattern()) {
            logger.info("Pod selected for JFR collection via name pattern");
            return true;
        }

        logger.info("Pod NOT selected for JFR collection");
        return false;
    }

    /**
     * Strategy 1: Check for explicit environment flag.
     * Set JFR_COLLECTOR_ENABLED=true on specific pods.
     */
    private boolean checkExplicitFlag() {
        String enabled = System.getenv("JFR_COLLECTOR_ENABLED");
        return "true".equalsIgnoreCase(enabled);
    }

    /**
     * Strategy 2: For StatefulSets, only pod-0 collects.
     * StatefulSet pods are named like: myapp-0, myapp-1, myapp-2
     */
    private boolean checkStatefulSetPod0() {
        String podName = System.getenv("HOSTNAME");
        if (podName == null) {
            podName = System.getenv("POD_NAME");
        }

        if (podName != null) {
            // Check if pod name ends with -0 (first pod in StatefulSet)
            return podName.matches(".*-0$");
        }

        return false;
    }

    /**
     * Strategy 3: Check if pod name matches a pattern.
     * Set JFR_COLLECTOR_POD_PATTERN env var with regex.
     * Example: ".*-collector.*" to match pods with "collector" in name
     */
    private boolean checkPodNamePattern() {
        String pattern = System.getenv("JFR_COLLECTOR_POD_PATTERN");
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        String podName = System.getenv("HOSTNAME");
        if (podName == null) {
            podName = System.getenv("POD_NAME");
        }

        if (podName != null) {
            return podName.matches(pattern);
        }

        return false;
    }

    /**
     * Get region information from environment.
     * Useful for logging and organizing dumps by region.
     *
     * @return region name or "unknown"
     */
    public String getRegion() {
        // Try common GCP metadata
        String region = System.getenv("GCP_REGION");
        if (region != null) return region;

        // Try Kubernetes node label (if downward API is configured)
        region = System.getenv("NODE_REGION");
        if (region != null) return region;

        // Try custom environment variable
        region = System.getenv("REGION");
        if (region != null) return region;

        // Fallback
        return "unknown";
    }

    /**
     * Get pod name for identification.
     *
     * @return pod name or "unknown"
     */
    public String getPodName() {
        String podName = System.getenv("HOSTNAME");
        if (podName == null) {
            podName = System.getenv("POD_NAME");
        }
        return podName != null ? podName : "unknown";
    }

    /**
     * Get namespace information.
     *
     * @return namespace or "default"
     */
    public String getNamespace() {
        String namespace = System.getenv("POD_NAMESPACE");
        return namespace != null ? namespace : "default";
    }
}
