package com.researchflow.model;

import java.util.Set;

public record AgentDescriptor(String name, Set<String> capabilities, Set<String> requiredTools) {
}
