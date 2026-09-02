package com.researchflow.agent.runtime;

public record ReActDecision(String decision, String reason, String nextInstruction) {
    public Outcome outcome() {
        if (decision == null) return Outcome.FAIL;
        try {
            return Outcome.valueOf(decision.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Outcome.FAIL;
        }
    }

    public String normalizedReason() {
        return reason == null || reason.isBlank() ? "当前观察结果满足节点输出要求" : reason.trim();
    }

    public String normalizedInstruction(String fallback) {
        return nextInstruction == null || nextInstruction.isBlank() ? fallback : nextInstruction.trim();
    }

    public enum Outcome {
        COMPLETE, RETRY, FAIL
    }
}
