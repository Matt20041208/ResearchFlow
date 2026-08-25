package com.researchflow.injection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaultInjectionRuntimeTest {
    private final FaultInjectionRuntime runtime = new FaultInjectionRuntime();

    @Test
    void injectsControlledErrors() {
        assertThrows(InjectedFaultException.class, () -> runtime.before("search",
                List.of(new InjectionRule("search", InjectionType.ERROR, 0, "模拟失败"))));
    }

    @Test
    void replacesResultsWithTypeCompatibleEmptyValues() {
        Object result = runtime.after("search", List.of("source"),
                List.of(new InjectionRule("search", InjectionType.EMPTY_RESULT, 0, null)));

        assertEquals(List.of(), result);
    }
}
