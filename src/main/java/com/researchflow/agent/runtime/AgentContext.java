package com.researchflow.agent.runtime;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AgentContext {
    private final String question;
    private final String workspaceId;
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public AgentContext(String question, String workspaceId) {
        this.question = question;
        this.workspaceId = workspaceId;
        values.put("question", question);
        values.put("workspaceId", workspaceId);
    }

    public String question() { return question; }
    public String workspaceId() { return workspaceId; }
    public void put(String key, Object value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
    public <T> T get(String key, Class<T> type) { return type.cast(values.get(key)); }
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalStateException("上下文不是列表: " + key);
        return list.stream().map(elementType::cast).toList();
    }
    public Map<String, Object> snapshot() { return Map.copyOf(values); }
}
