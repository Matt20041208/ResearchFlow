package com.researchflow.injection;

import com.researchflow.agent.runtime.MultiAgentOrchestrator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FaultInjectionRuntime {
    public void before(String nodeId, List<InjectionRule> rules) {
        for (InjectionRule rule : rulesFor(nodeId, rules)) {
            if (rule.type() == InjectionType.DELAY) delay(rule.delayMs());
            if (rule.type() == InjectionType.ERROR) {
                throw new InjectedFaultException(rule.message() == null || rule.message().isBlank()
                        ? "节点 " + nodeId + " 发生受控注入异常" : rule.message());
            }
        }
    }

    public Object after(String nodeId, Object result, List<InjectionRule> rules) {
        boolean empty = rulesFor(nodeId, rules).stream().anyMatch(rule -> rule.type() == InjectionType.EMPTY_RESULT);
        if (!empty) return result;
        if (result instanceof List<?>) return List.of();
        if (result instanceof String) return "";
        return null;
    }

    public boolean hasRule(String nodeId, List<InjectionRule> rules) {
        return rules.stream().anyMatch(rule -> rule.nodeId().equals(nodeId));
    }

    private List<InjectionRule> rulesFor(String nodeId, List<InjectionRule> rules) {
        return rules.stream().filter(rule -> rule.nodeId().equals(nodeId)).toList();
    }

    private void delay(long delayMs) {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MultiAgentOrchestrator.TaskCancelledException();
        }
    }
}
