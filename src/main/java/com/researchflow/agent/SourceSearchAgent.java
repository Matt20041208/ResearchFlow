package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceSearchAgent implements ResearchAgent<ResearchPlan, List<SourceDocument>> {
    public String name() { return "source-search-agent"; }

    public List<SourceDocument> execute(ResearchPlan plan) {
        return List.of(
                new SourceDocument("研究主题综述入口", "https://example.org/search?q=" + plan.searchQuery(),
                        "用于承载真实论文检索结果的适配器，目前返回演示来源。"),
                new SourceDocument("方法与应用案例", "https://example.org/methods",
                        "用于演示来源之间的横向比较和引用追踪。")
        );
    }
}
