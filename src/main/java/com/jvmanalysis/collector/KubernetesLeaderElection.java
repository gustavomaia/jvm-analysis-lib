package com.jvmanalysis.collector;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.Lock;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kubernetes leader election for JFR collection.
 * Ensures only one pod per region/namespace collects JFR dumps.
 */
public class KubernetesLeaderElection {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesLeaderElection.class);

    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final String lockName;
    private final String namespace;
    private final String podName;
    private LeaderElector leaderElector;
    private Thread electionThread;

    /**
     * Create a new leader election instance.
     *
     * @param lockName Name of the lease lock (e.g., "jfr-collector-leader")
     * @param namespace Kubernetes namespace
     * @param podName This pod's name
     */
    public KubernetesLeaderElection(String lockName, String namespace, String podName) {
        this.lockName = lockName;
        this.namespace = namespace;
        this.podName = podName;
    }

    /**
     * Start leader election. This is a blocking operation.
     * Run in a separate thread if you want non-blocking behavior.
     */
    public void start() throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        client.setHttpClient(client.getHttpClient()
                .newBuilder()
                .readTimeout(Duration.ZERO)
                .build());

        Lock lock = new LeaseLock(namespace, lockName, podName);

        LeaderElectionConfig config = new LeaderElectionConfig(
                lock,
                Duration.ofMillis(10000), // lease duration
                Duration.ofMillis(8000),  // renew deadline
                Duration.ofMillis(2000)   // retry period
        );

        leaderElector = new LeaderElector(config);

        logger.info("Starting leader election for lock: {} in namespace: {}", lockName, namespace);

        // Run election in background thread
        electionThread = new Thread(() -> {
            leaderElector.run(
                    () -> {
                        // On becoming leader
                        isLeader.set(true);
                        logger.info("🎯 Pod {} became the LEADER for JFR collection", podName);
                    },
                    () -> {
                        // On losing leadership
                        isLeader.set(false);
                        logger.info("Lost leadership for JFR collection");
                    },
                    podName -> {
                        // On new leader elected
                        logger.info("New leader elected: {}", podName);
                    }
            );
        });

        electionThread.setName("k8s-leader-election");
        electionThread.setDaemon(true);
        electionThread.start();

        logger.info("Leader election thread started");
    }

    /**
     * Check if this pod is currently the leader.
     *
     * @return true if leader
     */
    public boolean isLeader() {
        return isLeader.get();
    }

    /**
     * Stop leader election and release the lock.
     */
    public void stop() {
        if (electionThread != null) {
            electionThread.interrupt();
            try {
                electionThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isLeader.set(false);
        logger.info("Leader election stopped");
    }

    /**
     * Factory method to create leader election from environment variables.
     *
     * @return KubernetesLeaderElection instance or null if required env vars missing
     */
    public static KubernetesLeaderElection fromEnvironment() {
        String podName = System.getenv("HOSTNAME");
        if (podName == null) {
            podName = System.getenv("POD_NAME");
        }

        String namespace = System.getenv("POD_NAMESPACE");
        if (namespace == null) {
            namespace = "default";
        }

        String region = System.getenv("REGION");
        if (region == null) {
            region = System.getenv("GCP_REGION");
        }
        if (region == null) {
            region = "default";
        }

        if (podName == null) {
            logger.warn("POD_NAME or HOSTNAME not set, leader election cannot be initialized");
            return null;
        }

        // Create lock name with region to enable per-region leaders
        String lockName = "jfr-collector-leader-" + region;

        return new KubernetesLeaderElection(lockName, namespace, podName);
    }
}
