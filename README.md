# JVM Analysis Library

Automated JVM performance analysis using Java Flight Recorder (JFR) dumps and Claude AI for intelligent optimization recommendations.

## Features

- ✅ **Automated JFR Recording** - In-app recording with configurable duration and settings
- ✅ **Comprehensive Analysis** - CPU profiling, memory allocation, GC stats, thread issues
- ✅ **AI-Powered Insights** - Claude AI generates actionable optimization recommendations
- ✅ **Multi-Region Support** - Kubernetes leader election for per-region collection
- ✅ **Source Code Integration** - Fetches code via Git API or bundled sources
- ✅ **Async Processing** - Full CompletableFuture-based pipeline for efficiency
- ✅ **Cloud Storage** - Automatic upload to Google Cloud Storage
- ✅ **Slack Integration** - Formatted reports sent directly to Slack
- ✅ **Method Whitelisting** - Focus analysis on your code, exclude dependencies

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.jvmanalysis</groupId>
    <artifactId>jvm-analysis-lib</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Environment Variables

```bash
# Claude AI
export CLAUDE_API_KEY="your-claude-api-key"

# Google Cloud Storage
export GCS_BUCKET="your-jfr-dumps-bucket"
export GCP_PROJECT_ID="your-project-id"

# Slack
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK"

# GitHub (for source code fetching)
export GITHUB_TOKEN="ghp_yourtoken"
export GITHUB_REPO="yourorg/yourrepo"

# Kubernetes (set via downward API in deployment)
export POD_NAME="myapp-abc123"
export POD_NAMESPACE="production"
export REGION="us-east1"

# Pod Selection Strategy
export JFR_SELECTION_STRATEGY="leader-election"  # or "statefulset", "explicit", "pattern"
```

### 3. Basic Usage

```java
import com.jvmanalysis.JvmAnalysisOrchestrator;
import com.jvmanalysis.config.JvmAnalysisConfig;

public class MyApplication {
    public static void main(String[] args) throws Exception {
        // Configure
        JvmAnalysisConfig config = new JvmAnalysisConfig();

        // Claude
        JvmAnalysisConfig.ClaudeConfig claude = new JvmAnalysisConfig.ClaudeConfig();
        claude.setApiKey(System.getenv("CLAUDE_API_KEY"));
        config.setClaude(claude);

        // GCS
        JvmAnalysisConfig.GcsConfig gcs = new JvmAnalysisConfig.GcsConfig();
        gcs.setBucketName(System.getenv("GCS_BUCKET"));
        gcs.setProjectId(System.getenv("GCP_PROJECT_ID"));
        config.setGcs(gcs);

        // Slack
        JvmAnalysisConfig.SlackConfig slack = new JvmAnalysisConfig.SlackConfig();
        slack.setWebhookUrl(System.getenv("SLACK_WEBHOOK_URL"));
        config.setSlack(slack);

        // Analysis - focus on your code
        JvmAnalysisConfig.AnalysisConfig analysis = new JvmAnalysisConfig.AnalysisConfig();
        analysis.getPackageWhitelist().add("com.yourcompany");
        config.setAnalysis(analysis);

        // Create orchestrator
        JvmAnalysisOrchestrator orchestrator = new JvmAnalysisOrchestrator(config);

        // Run analysis (async)
        orchestrator.runAnalysisPipeline()
            .thenAccept(result -> {
                System.out.println("Analysis complete! " + result.getReportUrl());
            });
    }
}
```

## Pod Selection Strategies

Choose how to select which pods collect JFR dumps:

### 1. Leader Election (Recommended for Production)

One pod per region is elected as leader automatically.

```bash
export JFR_SELECTION_STRATEGY="leader-election"
```

**Requires:** Kubernetes RBAC permissions for lease resources (see deployment example below).

### 2. StatefulSet Pod-0

Always use the first pod in a StatefulSet.

```bash
export JFR_SELECTION_STRATEGY="statefulset"
```

### 3. Explicit Flag

Manually enable specific pods.

```bash
export JFR_SELECTION_STRATEGY="explicit"
export JFR_COLLECTOR_ENABLED="true"
```

### 4. Name Pattern

Select pods matching a regex pattern.

```bash
export JFR_SELECTION_STRATEGY="pattern"
export JFR_COLLECTOR_POD_PATTERN=".*-collector.*"
```

## Source Code Bundling

To include source code in your Docker image for better analysis:

### Update pom.xml

The provided `pom.xml` already includes source bundling configuration:

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/java</directory>
            <targetPath>sources</targetPath>
            <includes>
                <include>**/*.java</include>
            </includes>
        </resource>
    </resources>
</build>
```

### Build Docker Image

```dockerfile
# See Dockerfile.example for full implementation
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG GIT_COMMIT_SHA
ENV GIT_COMMIT_SHA=${GIT_COMMIT_SHA}

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
COPY --from=builder /build/target/*.jar app.jar

ENV GIT_COMMIT_SHA=${GIT_COMMIT_SHA}
```

Build with:
```bash
docker build --build-arg GIT_COMMIT_SHA=$(git rev-parse HEAD) -t myapp:latest .
```

## Kubernetes Deployment

Example deployment with leader election and downward API:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jfr-collector
  namespace: production

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: jfr-leader-election
  namespace: production
rules:
- apiGroups: ["coordination.k8s.io"]
  resources: ["leases"]
  verbs: ["get", "create", "update"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jfr-leader-election
  namespace: production
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: jfr-leader-election
subjects:
- kind: ServiceAccount
  name: jfr-collector
  namespace: production

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      serviceAccountName: jfr-collector
      containers:
      - name: myapp
        image: myapp:latest
        env:
        # Downward API - inject pod metadata
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: NODE_REGION
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels['topology.kubernetes.io/region']

        # JFR Configuration
        - name: JFR_SELECTION_STRATEGY
          value: "leader-election"

        # Claude AI
        - name: CLAUDE_API_KEY
          valueFrom:
            secretKeyRef:
              name: claude-api-secret
              key: api-key

        # GCS
        - name: GCS_BUCKET
          value: "my-jfr-dumps"
        - name: GCP_PROJECT_ID
          value: "my-project"

        # Slack
        - name: SLACK_WEBHOOK_URL
          valueFrom:
            secretKeyRef:
              name: slack-webhook-secret
              key: url

        # GitHub
        - name: GITHUB_TOKEN
          valueFrom:
            secretKeyRef:
              name: github-token-secret
              key: token
        - name: GITHUB_REPO
          value: "myorg/myrepo"
```

## Scheduled Analysis with CronJob

Run JFR analysis on a schedule:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: jfr-analysis
  namespace: production
spec:
  schedule: "0 */6 * * *"  # Every 6 hours
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: jfr-collector
          containers:
          - name: analyzer
            image: myapp:latest
            command: ["java", "-jar", "app.jar", "--run-jfr-analysis"]
            env:
            # ... same env vars as deployment ...
          restartPolicy: OnFailure
```

## Configuration Reference

### JFR Configuration

```java
JvmAnalysisConfig.JfrConfig jfr = new JvmAnalysisConfig.JfrConfig();
jfr.setDuration(Duration.ofMinutes(1));  // Recording duration
jfr.setSettings("profile");              // "default" or "profile"
jfr.setCompressOnUpload(true);           // GZIP compression
jfr.setLocalStoragePath("/tmp/jfr-dumps");
```

### Analysis Configuration

```java
JvmAnalysisConfig.AnalysisConfig analysis = new JvmAnalysisConfig.AnalysisConfig();

// Whitelist packages to analyze
analysis.getPackageWhitelist().add("com.yourcompany");
analysis.getPackageWhitelist().add("com.yourorg");

// Blacklist specific packages
analysis.getPackageBlacklist().add("com.yourcompany.internal");

// Whitelist specific methods
analysis.getMethodWhitelist().add("com.yourcompany.HotClass.criticalMethod");

// Configure what to analyze
analysis.setAnalyzeCpu(true);
analysis.setAnalyzeMemory(true);
analysis.setAnalyzeGc(true);
analysis.setAnalyzeThreads(true);

// Number of top items to include
analysis.setTopHotMethodsCount(20);
analysis.setTopAllocationSitesCount(20);
```

### Claude Configuration

```java
JvmAnalysisConfig.ClaudeConfig claude = new JvmAnalysisConfig.ClaudeConfig();
claude.setApiKey("your-api-key");
claude.setModel("claude-sonnet-4-5-20250929");
claude.setMaxTokens(4096);
```

## Example Output

The analysis generates comprehensive reports:

```markdown
# JVM Performance Analysis Report

**Region**: us-east1
**Pod**: myapp-abc123
**Recording Duration**: 60000 ms

**CPU Samples**: 15234
**Hot Methods Found**: 20

**Total Allocations**: 1523421 (1245.32 MB)

**GC Collections**: 45
**GC Pause Time**: 234 ms (longest: 12 ms)

---

## Critical Issues

1. **High CPU in JSON serialization**: `com.fasterxml.jackson.databind.ObjectMapper.writeValue`
   consuming 23% CPU

2. **Excessive String allocations**: 450 MB allocated in `StringBuilder.toString()` calls

3. **GC pressure**: High allocation rate causing frequent young gen collections

## CPU Optimization Recommendations

### 1. Cache Jackson ObjectMapper instances (23% CPU reduction expected)
Currently creating new ObjectMapper on every request. Use a singleton...

[... more detailed recommendations ...]
```

## Architecture

```
┌─────────────────┐
│  Pod Selection  │ ◄── Leader Election / StatefulSet / Explicit
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  JFR Recording  │ ◄── jdk.jfr.Recording API
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────┐
│         Parallel Parsing                     │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐       │
│  │ CPU  │ │Memory│ │  GC  │ │Thread│       │
│  └──────┘ └──────┘ └──────┘ └──────┘       │
└────────┬─────────────────────────────────────┘
         │
         ├──► Upload to GCS (async)
         │
         ▼
┌─────────────────┐
│ Source Fetcher  │ ◄── Git API / Bundled / Decompile
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Claude AI      │ ◄── Async analysis with recommendations
└────────┬────────┘
         │
         ├──► Upload Report to GCS (async)
         │
         ▼
┌─────────────────┐
│ Slack Notifier  │ ◄── Send formatted report
└─────────────────┘
```

## Best Practices

1. **Use leader election** in production to avoid redundant dumps
2. **Whitelist your packages** to focus analysis on your code
3. **Bundle sources** in Docker images for accurate recommendations
4. **Schedule during low traffic** periods to minimize impact
5. **Monitor GCS costs** - compress dumps and set retention policies
6. **Set up Slack alerts** for critical performance issues

## Troubleshooting

### Leader election not working

- Check RBAC permissions for `coordination.k8s.io/leases`
- Verify POD_NAME and POD_NAMESPACE are set
- Check logs for election events

### Source code not found

- Verify GIT_COMMIT_SHA is set during build
- Check GITHUB_TOKEN has read permissions
- Ensure GITHUB_REPO format is "owner/repo"

### Claude API rate limits

- Reduce analysis frequency
- Use smaller `maxTokens` value
- Consider caching results

## License

MIT

## Contributing

Pull requests welcome!
