package com.researchflow.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.injection.InjectionRule;
import com.researchflow.injection.InjectionType;
import com.researchflow.llm.SpringAiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ValidationAssessmentService {
    private static final String SYSTEM_PROMPT = """
            你是 Agent 链路验证判定器。根据场景期望、注入规则和实际执行结果判断：
            EXPECTED_BEHAVIOR：系统表现符合场景期望；
            POTENTIAL_DEFECT：实际表现违反场景期望，可能存在缺陷；
            INCONCLUSIVE：证据不足。
            只输出结构化 JSON：{"result":"...","reason":"...","evidence":"..."}。
            不代替开发者最终判断。""";

    private final SpringAiClient aiClient;
    private final ObjectMapper objectMapper;

    public ValidationAssessmentService(SpringAiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public ValidationAssessment assess(String expectation, List<InjectionRule> rules,
                                       String outputSummary, String error, Object trace) {
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "expectation", expectation == null ? "" : expectation,
                    "rules", rules,
                    "outputSummary", outputSummary == null ? "" : outputSummary,
                    "error", error == null ? "" : error,
                    "trace", trace));
            var result = aiClient.entity(SYSTEM_PROMPT, payload, ValidationAssessment.class);
            if (result.isPresent() && result.get().result() != null) return result.get();
        } catch (Exception ignored) {}
        return fallback(expectation, rules, outputSummary, error);
    }

    private ValidationAssessment fallback(String expectation, List<InjectionRule> rules,
                                          String outputSummary, String error) {
        String expected = expectation == null ? "" : expectation;
        boolean shouldSurvive = contains(expected, "仍能", "降级", "保留", "完成", "一致");
        boolean observedFailure = error != null && !error.isBlank();
        boolean faultRule = rules.stream().anyMatch(rule -> rule.type() == InjectionType.ERROR
                || rule.type() == InjectionType.EMPTY_RESULT);
        if (observedFailure && shouldSurvive) {
            return new ValidationAssessment(AutomaticAssessment.POTENTIAL_DEFECT,
                    "场景期望链路保持可用，但注入后链路失败", error);
        }
        if (observedFailure && faultRule) {
            return new ValidationAssessment(AutomaticAssessment.EXPECTED_BEHAVIOR,
                    "受控故障成功触发并被链路观察到", error);
        }
        if (!observedFailure && contains(expected, "失败", "阻断", "拒绝")) {
            return new ValidationAssessment(AutomaticAssessment.POTENTIAL_DEFECT,
                    "场景期望阻断，但链路仍然完成", outputSummary == null ? "" : outputSummary);
        }
        if (!observedFailure) {
            return new ValidationAssessment(AutomaticAssessment.EXPECTED_BEHAVIOR,
                    "链路在注入条件下完成", outputSummary == null ? "" : outputSummary);
        }
        return new ValidationAssessment(AutomaticAssessment.INCONCLUSIVE, "证据不足，需要人工检查", "");
    }

    private boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
