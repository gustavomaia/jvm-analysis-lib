package com.jvmanalysis.comparison;

import com.jvmanalysis.model.AnalysisSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of comparing two analysis snapshots.
 * Highlights risks (regressions) and opportunities (improvements >= 10%).
 */
public class ComparisonResult {

    private AnalysisSnapshot current;
    private AnalysisSnapshot previous;

    private List<Risk> risks = new ArrayList<>();
    private List<Opportunity> opportunities = new ArrayList<>();
    private List<Change> changes = new ArrayList<>();
    private List<Suggestion> suggestions = new ArrayList<>();

    private boolean hasRegressions;
    private boolean hasSignificantImprovements;

    public AnalysisSnapshot getCurrent() {
        return current;
    }

    public void setCurrent(AnalysisSnapshot current) {
        this.current = current;
    }

    public AnalysisSnapshot getPrevious() {
        return previous;
    }

    public void setPrevious(AnalysisSnapshot previous) {
        this.previous = previous;
    }

    public List<Risk> getRisks() {
        return risks;
    }

    public void setRisks(List<Risk> risks) {
        this.risks = risks;
    }

    public List<Opportunity> getOpportunities() {
        return opportunities;
    }

    public void setOpportunities(List<Opportunity> opportunities) {
        this.opportunities = opportunities;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public void setChanges(List<Change> changes) {
        this.changes = changes;
    }

    public boolean hasRegressions() {
        return hasRegressions;
    }

    public void setHasRegressions(boolean hasRegressions) {
        this.hasRegressions = hasRegressions;
    }

    public boolean hasSignificantImprovements() {
        return hasSignificantImprovements;
    }

    public void setHasSignificantImprovements(boolean hasSignificantImprovements) {
        this.hasSignificantImprovements = hasSignificantImprovements;
    }

    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<Suggestion> suggestions) {
        this.suggestions = suggestions;
    }

    /**
     * Risk - potential performance regression that needs attention.
     */
    public static class Risk {
        private String category; // CPU, MEMORY, GC, THREADS
        private String severity; // CRITICAL, HIGH, MEDIUM, LOW
        private String description;
        private double percentageChange;
        private String metric;
        private Object previousValue;
        private Object currentValue;

        public Risk(String category, String severity, String description,
                    double percentageChange, String metric,
                    Object previousValue, Object currentValue) {
            this.category = category;
            this.severity = severity;
            this.description = description;
            this.percentageChange = percentageChange;
            this.metric = metric;
            this.previousValue = previousValue;
            this.currentValue = currentValue;
        }

        public String getCategory() {
            return category;
        }

        public String getSeverity() {
            return severity;
        }

        public String getDescription() {
            return description;
        }

        public double getPercentageChange() {
            return percentageChange;
        }

        public String getMetric() {
            return metric;
        }

        public Object getPreviousValue() {
            return previousValue;
        }

        public Object getCurrentValue() {
            return currentValue;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (%.1f%% change: %s → %s)",
                    severity, category, description, percentageChange, previousValue, currentValue);
        }
    }

    /**
     * Opportunity - performance improvement >= 10%.
     */
    public static class Opportunity {
        private String category;
        private String description;
        private double percentageImprovement;
        private String metric;
        private Object previousValue;
        private Object currentValue;

        public Opportunity(String category, String description,
                           double percentageImprovement, String metric,
                           Object previousValue, Object currentValue) {
            this.category = category;
            this.description = description;
            this.percentageImprovement = percentageImprovement;
            this.metric = metric;
            this.previousValue = previousValue;
            this.currentValue = currentValue;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public double getPercentageImprovement() {
            return percentageImprovement;
        }

        public String getMetric() {
            return metric;
        }

        public Object getPreviousValue() {
            return previousValue;
        }

        public Object getCurrentValue() {
            return currentValue;
        }

        @Override
        public String toString() {
            return String.format("[OPPORTUNITY] %s: %s (%.1f%% improvement: %s → %s)",
                    category, description, percentageImprovement, previousValue, currentValue);
        }
    }

    /**
     * Change - neutral or minor change for informational purposes.
     */
    public static class Change {
        private String category;
        private String description;
        private double percentageChange;
        private Object previousValue;
        private Object currentValue;

        public Change(String category, String description,
                      double percentageChange,
                      Object previousValue, Object currentValue) {
            this.category = category;
            this.description = description;
            this.percentageChange = percentageChange;
            this.previousValue = previousValue;
            this.currentValue = currentValue;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public double getPercentageChange() {
            return percentageChange;
        }

        public Object getPreviousValue() {
            return previousValue;
        }

        public Object getCurrentValue() {
            return currentValue;
        }
    }
}
