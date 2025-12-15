package com.jvmanalysis.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for determining if this pod should collect JFR dumps.
 * Useful for multi-region Kubernetes deployments where you want
 * only one pod per region to collect dumps.
 *
 * Configuration via environment variables:
 * - JFR_SELECTION_STRATEGY: "leader-election", "statefulset", "explicit", "pattern" (default: "leader-election")
 * - JFR_COLLECTOR_ENABLED: "true" to explicitly enable collection
 * - JFR_COLLECTOR_POD_PATTERN: Regex pattern for pod name matching
 */
public class PodSelectionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PodSelectionStrategy.class);

    private final KubernetesLeaderElection leaderElection;
    private final String strategy;

    /**
     * Create pod selection strategy with optional leader election.
     *
     * @param leaderElection Leader election instance (can be null)
     */
    public PodSelectionStrategy(KubernetesLeaderElection leaderElection) {
        this.leaderElection = leaderElection;
        this.strategy = System.getenv().getOrDefault("JFR_SELECTION_STRATEGY", "leader-election");
        logger.info("Pod selection strategy initialized: {}", strategy);
    }

    /**
     * Create with default settings (auto-detect from environment).
     */
    public PodSelectionStrategy() {
        this(null);
    }

    /**
     * Check if this pod should collect JFR dumps based on configured strategy.
     *
     * @return true if this pod should collect
     */
    public boolean shouldCollect() {
        switch (strategy.toLowerCase()) {
            case "leader-election":
                return checkLeaderElection();

            case "statefulset":
                if (checkStatefulSetPod0()) {
                    logger.info("Pod selected for JFR collection as StatefulSet pod-0");
                    return true;
                }
                break;

            case "explicit":
                if (checkExplicitFlag()) {
                    logger.info("Pod selected for JFR collection via explicit flag");
                    return true;
                }
                break;

            case "pattern":
                if (checkPodNamePattern()) {
                    logger.info("Pod selected for JFR collection via name pattern");
                    return true;
                }
                break;

            default:
                logger.warn("Unknown strategy: {}, falling back to leader-election", strategy);
                return checkLeaderElection();
        }

        logger.info("Pod NOT selected for JFR collection");
        return false;
    }

    /**
     * Strategy 0: Kubernetes leader election (recommended for production).
     * Only the elected leader pod collects dumps.
     */
    private boolean checkLeaderElection() {
        if (leaderElection != null && leaderElection.isLeader()) {
            logger.info("Pod selected for JFR collection as LEADER");
            return true;
        } else if (leaderElection == null) {
            logger.warn("Leader election not initialized, falling back to other strategies");
            // Fallback to other strategies if leader election isn't available
            return checkExplicitFlag() || checkStatefulSetPod0() || checkPodNamePattern();
        }
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
