package com.researchflow.agent.runtime;

import com.researchflow.agent.ComparisonAgent;
import com.researchflow.agent.CriticAgent;
import com.researchflow.agent.EvidenceAgent;
import com.researchflow.agent.FactCheckerAgent;
import com.researchflow.agent.ModeratorAgent;
import com.researchflow.agent.PlannerAgent;
import com.researchflow.agent.PrivateKnowledgeAgent;
import com.researchflow.agent.ResearchPlan;
import com.researchflow.agent.SourceSearchAgent;
import com.researchflow.agent.SourceMergeAgent;
import com.researchflow.agent.WriterAgent;
import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class AgentRegistry {
    private final Map<String, SubAgent> agents;

    public AgentRegistry(PlannerAgent planner, SourceSearchAgent search, PrivateKnowledgeAgent privateKnowledge,
                          SourceMergeAgent sourceMerge, EvidenceAgent evidence,
                          ComparisonAgent comparison, CriticAgent critic, FactCheckerAgent factChecker,
                          ModeratorAgent moderator, WriterAgent writer) {
        this.agents = Map.ofEntries(
                Map.entry("planner-agent", adapter(planner.name(), Set.of("planning", "decomposition"), Set.of(),
                        context -> planner.execute(context.question()))),
                Map.entry("source-search-agent", adapter(search.name(), Set.of("paper-search", "retrieval"),
                        Set.of("crossref-search"),
                        context -> search.execute(context.get("plan", ResearchPlan.class)))),
                Map.entry("private-knowledge-agent", adapter(privateKnowledge.name(), Set.of("private-search", "retrieval"),
                        Set.of("private-knowledge-search"),
                        context -> privateKnowledge.execute(context.workspaceId(), context.question()))),
                Map.entry("source-merge-agent", adapter(sourceMerge.name(), Set.of("source-merge", "deduplication"), Set.of(),
                        context -> sourceMerge.execute(
                                context.getList("externalSources", SourceDocument.class),
                                context.getList("privateSources", SourceDocument.class)))),
                Map.entry("evidence-agent", adapter(evidence.name(), Set.of("evidence", "extraction"), Set.of(),
                        context -> evidence.execute(context.getList("sources", SourceDocument.class)))),
                Map.entry("comparison-agent", adapter(comparison.name(), Set.of("comparison", "analysis"), Set.of(),
                        context -> comparison.execute(new ComparisonAgent.Input(
                                context.get("plan", ResearchPlan.class), context.get("evidence", String.class))))),
                Map.entry("critic-agent", adapter(critic.name(), Set.of("critique", "gap-analysis"),
                        Set.of("llm-completion"), (context, instruction) -> critic.execute(new CriticAgent.Input(
                                context.question(), context.get("comparison", String.class),
                                context.get("evidence", String.class), instruction)))),
                Map.entry("fact-checker-agent", adapter(factChecker.name(), Set.of("fact-checking", "citation-audit"),
                        Set.of("llm-completion"), (context, instruction) -> factChecker.execute(new FactCheckerAgent.Input(
                                context.question(), context.get("evidence", String.class),
                                context.getList("sources", SourceDocument.class), instruction)))),
                Map.entry("moderator-agent", adapter(moderator.name(), Set.of("deliberation", "consensus"),
                        Set.of("llm-completion"), (context, instruction) -> moderator.execute(new ModeratorAgent.Input(
                                context.question(), context.get("comparison", String.class),
                                context.get("critic", String.class), context.get("factCheck", String.class),
                                (String) context.get("risk"), instruction)))),
                Map.entry("writer-agent", adapter(writer.name(), Set.of("writing", "synthesis"), Set.of("llm-completion"),
                        (context, instruction) -> {
                            String comparisonText = context.get("comparison", String.class);
                            return writer.execute(new WriterAgent.Input(context.question(), comparisonText,
                                    context.get("moderation", String.class),
                                    context.getList("sources", SourceDocument.class), instruction));
                        })),
                Map.entry("risk-agent", adapter("risk-agent", Set.of("risk-analysis"), Set.of(), context ->
                        "风险分析：基于当前证据，应重点核验数据偏差、评测可重复性、隐私合规和实际部署成本。")),
                Map.entry("publisher-agent", adapter("publisher-agent", Set.of("publication"), Set.of("report-publish"),
                        context -> "报告已通过受控发布工具处理"))
        );
    }

    public SubAgent get(String name) {
        SubAgent agent = agents.get(name);
        if (agent == null) throw new IllegalArgumentException("未注册的 Sub-Agent: " + name);
        return agent;
    }

    public List<String> names() { return agents.keySet().stream().sorted().toList(); }

    public List<SubAgent> all() {
        return agents.values().stream().sorted(java.util.Comparator.comparing(SubAgent::name)).toList();
    }

    private SubAgent adapter(String name, Set<String> capabilities, Set<String> requiredTools,
                             Function<AgentContext, Object> action) {
        return adapter(name, capabilities, requiredTools, (context, instruction) -> action.apply(context), false);
    }

    private SubAgent adapter(String name, Set<String> capabilities, Set<String> requiredTools,
                             BiFunction<AgentContext, String, Object> action) {
        return adapter(name, capabilities, requiredTools, action, true);
    }

    private SubAgent adapter(String name, Set<String> capabilities, Set<String> requiredTools,
                             BiFunction<AgentContext, String, Object> action, boolean supportsRetry) {
        return new SubAgent() {
            public String name() { return name; }
            public Set<String> capabilities() { return capabilities; }
            public Set<String> requiredTools() { return requiredTools; }
            public Object execute(AgentContext context) { return action.apply(context, "完成当前节点目标"); }
            public Object execute(AgentContext context, String instruction) { return action.apply(context, instruction); }
            public boolean supportsRetry() { return supportsRetry; }
        };
    }
}
