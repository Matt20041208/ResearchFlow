package com.researchflow.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.injection.InjectionRule;
import com.researchflow.injection.InjectionType;
import com.researchflow.llm.SpringAiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationAssessmentServiceTest {
    @Test
    void marksFailureAsPotentialDefectWhenScenarioExpectedDegradation() {
        SpringAiClient ai = mock(SpringAiClient.class);
        when(ai.entity(anyString(), anyString(), eq(ValidationAssessment.class))).thenReturn(Optional.empty());
        ValidationAssessmentService service = new ValidationAssessmentService(ai, new ObjectMapper());

        var result = service.assess("验证降级链路是否仍能完成并保留来源",
                List.of(new InjectionRule("risk", InjectionType.EMPTY_RESULT, 0, null)),
                "注入后观察到链路失败", "risk-agent 返回空文本", List.of());

        assertEquals(AutomaticAssessment.POTENTIAL_DEFECT, result.result());
    }
}
