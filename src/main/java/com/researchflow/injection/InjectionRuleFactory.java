package com.researchflow.injection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.persistence.ScenarioEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class InjectionRuleFactory {
    private final ObjectMapper objectMapper;

    public InjectionRuleFactory(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public List<InjectionRule> create(ScenarioEntity scenario, SystemPlan plan) {
        Set<String> validNodes = plan.nodes().stream().map(node -> node.id())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<InjectionRule> structured = structured(scenario.getRulesJson(), validNodes);
        if (!structured.isEmpty()) return structured;
        List<String> targets = targets(scenario, validNodes);
        if (targets.isEmpty()) throw new IllegalArgumentException("场景没有匹配到可注入节点");

        Map<String, InjectionRule> rules = new LinkedHashMap<>();
        JsonNode payload = parse(scenario.getInjectedData());
        long delayMs = payload.path("delayMs").asLong(0);
        if (delayMs > 0) add(rules, new InjectionRule(targets.get(0), InjectionType.DELAY, delayMs,
                "场景注入延迟"));
        if (payload.path("error").asBoolean(false) || payload.path("throw").asBoolean(false)) {
            add(rules, new InjectionRule(targets.get(0), InjectionType.ERROR, 0, "场景注入异常"));
        }
        if (payload.has("data") && payload.path("data").isArray() && payload.path("data").isEmpty()) {
            add(rules, new InjectionRule(targets.get(targets.size() - 1), InjectionType.EMPTY_RESULT, 0,
                    "场景注入空结果"));
        }

        String trigger = (scenario.getTrigger() + " " + scenario.getInjectedData()).toLowerCase(Locale.ROOT);
        if (contains(trigger, "延迟", "超时", "慢响应", "delay", "timeout")
                && rules.values().stream().noneMatch(rule -> rule.type() == InjectionType.DELAY)) {
            add(rules, new InjectionRule(targets.get(0), InjectionType.DELAY, 3_000, "场景推断延迟"));
        }
        if (contains(trigger, "空数据", "空列表", "empty")
                && rules.values().stream().noneMatch(rule -> rule.type() == InjectionType.EMPTY_RESULT)) {
            add(rules, new InjectionRule(targets.get(targets.size() - 1), InjectionType.EMPTY_RESULT, 0,
                    "场景推断空结果"));
        }
        if (contains(trigger, "失败", "失效", "异常", "error", "fail") && rules.isEmpty()) {
            add(rules, new InjectionRule(targets.get(0), InjectionType.ERROR, 0, "场景推断异常"));
        }
        if (rules.isEmpty()) {
            add(rules, new InjectionRule(targets.get(0), InjectionType.DELAY, 1_000, "默认受控延迟"));
        }
        return List.copyOf(rules.values());
    }

    private List<InjectionRule> structured(String rulesJson, Set<String> validNodes) {
        if (rulesJson == null || rulesJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(rulesJson, new TypeReference<List<InjectionRule>>() {}).stream()
                    .filter(rule -> validNodes.contains(rule.nodeId())).limit(4).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> targets(ScenarioEntity scenario, Set<String> validNodes) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        JsonNode payload = parse(scenario.getInjectedData());
        if (payload.path("node").isTextual()) targets.add(payload.path("node").asText());
        payload.path("nodes").forEach(node -> { if (node.isTextual()) targets.add(node.asText()); });
        for (String value : scenario.getNodeCombination().split("[,+\\s]+")) {
            if (!value.isBlank()) targets.add(value.trim());
        }
        return targets.stream().filter(validNodes::contains).toList();
    }

    private JsonNode parse(String value) {
        try { return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception ignored) { return objectMapper.createObjectNode(); }
    }

    private void add(Map<String, InjectionRule> rules, InjectionRule rule) {
        rules.putIfAbsent(rule.nodeId() + ':' + rule.type(), rule);
    }

    private boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
