package com.researchflow.scenario;

import java.util.List;

public record ScenarioSeed(String title, String nodeCombination, String trigger,
                           String injectedData, String expectation, String risk) {
}
