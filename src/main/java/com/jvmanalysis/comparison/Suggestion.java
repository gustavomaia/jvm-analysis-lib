package com.jvmanalysis.comparison;

/**
 * Actionable suggestion with complexity vs gain analysis.
 * Helps prioritize which optimizations to implement first.
 */
public class Suggestion {

    private String id;
    private String title;
    private String description;
    private String category; // CPU, MEMORY, GC, THREADS
    private Complexity complexity;
    private Gain expectedGain;
    private int priorityScore; // 0-100, based on gain/complexity ratio
    private String implementation; // Step-by-step how-to
    private String relatedMethod; // Which method/class to change
    private double estimatedImprovementPercent;

    public enum Complexity {
        TRIVIAL(1, "Trivial - < 1 hour"),      // Config change, JVM flag
        LOW(2, "Low - 1-4 hours"),             // Simple refactor, add caching
        MEDIUM(3, "Medium - 1-2 days"),        // Algorithm change, restructure
        HIGH(4, "High - 3-5 days"),            // Major refactor, new library
        VERY_HIGH(5, "Very High - 1+ weeks");  // Architecture change

        private final int level;
        private final String description;

        Complexity(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Gain {
        MINIMAL(1, "< 5% improvement"),
        LOW(2, "5-10% improvement"),
        MEDIUM(3, "10-20% improvement"),
        HIGH(4, "20-40% improvement"),
        CRITICAL(5, "40%+ improvement");

        private final int level;
        private final String description;

        Gain(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }
    }

    public Suggestion(String title, String description, String category,
                      Complexity complexity, Gain expectedGain) {
        this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.title = title;
        this.description = description;
        this.category = category;
        this.complexity = complexity;
        this.expectedGain = expectedGain;
        this.priorityScore = calculatePriority(complexity, expectedGain);
    }

    /**
     * Calculate priority score (0-100) based on gain/complexity ratio.
     * Higher is better - high gain + low complexity = high priority.
     */
    private int calculatePriority(Complexity complexity, Gain gain) {
        // Priority = (Gain * 20) - (Complexity * 10) + 50
        // This gives preference to high gain, low complexity items
        int score = (gain.getLevel() * 20) - (complexity.getLevel() * 10) + 50;
        return Math.max(0, Math.min(100, score)); // Clamp to 0-100
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Complexity getComplexity() {
        return complexity;
    }

    public void setComplexity(Complexity complexity) {
        this.complexity = complexity;
    }

    public Gain getExpectedGain() {
        return expectedGain;
    }

    public void setExpectedGain(Gain expectedGain) {
        this.expectedGain = expectedGain;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

    public String getRelatedMethod() {
        return relatedMethod;
    }

    public void setRelatedMethod(String relatedMethod) {
        this.relatedMethod = relatedMethod;
    }

    public double getEstimatedImprovementPercent() {
        return estimatedImprovementPercent;
    }

    public void setEstimatedImprovementPercent(double estimatedImprovementPercent) {
        this.estimatedImprovementPercent = estimatedImprovementPercent;
    }

    /**
     * Get priority label for display.
     */
    public String getPriorityLabel() {
        if (priorityScore >= 80) return "🔥 URGENT";
        if (priorityScore >= 60) return "⭐ HIGH";
        if (priorityScore >= 40) return "📌 MEDIUM";
        return "💡 LOW";
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Complexity: %s | Gain: %s | Priority: %d",
                category, title, complexity.description, expectedGain.description, priorityScore);
    }
}
