package com.researchflow.agent;

public interface ResearchAgent<I, O> {
    String name();
    O execute(I input);
}
