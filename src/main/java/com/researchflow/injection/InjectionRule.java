package com.researchflow.injection;

public record InjectionRule(String nodeId, InjectionType type, long delayMs, String message) {
    public InjectionRule {
        if (nodeId == null || nodeId.isBlank()) throw new IllegalArgumentException("注入节点不能为空");
        if (type == null) throw new IllegalArgumentException("注入类型不能为空");
        delayMs = Math.max(0, Math.min(delayMs, 10_000));
    }
}
