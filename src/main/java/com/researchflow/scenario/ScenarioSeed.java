package com.researchflow.scenario;

import com.researchflow.injection.InjectionRule;
import java.util.List;

public record ScenarioSeed(String title, String nodeCombination, String trigger,
                           String injectedData, String expectation, String risk,
                           List<InjectionRule> injectionRules) {
}
