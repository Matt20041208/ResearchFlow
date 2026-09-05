package com.researchflow.tool;

import com.researchflow.agent.runtime.SubAgent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ToolRegistry {
    private final Map<String, ToolDescriptor> tools = Map.of(
            "crossref-search", new ToolDescriptor("crossref-search", "检索 Crossref 论文元数据", ToolRisk.READ_ONLY),
            "private-knowledge-search", new ToolDescriptor("private-knowledge-search", "检索当前工作区私有知识库", ToolRisk.READ_ONLY),
            "llm-completion", new ToolDescriptor("llm-completion", "调用 OpenAI 兼容模型", ToolRisk.EXTERNAL_CALL),
            "report-publish", new ToolDescriptor("report-publish", "将研究报告发布到外部目标", ToolRisk.HIGH_RISK)
    );
    private final McpToolGateway mcpGateway;

    public ToolRegistry() {
        this.mcpGateway = null;
    }

    @Autowired
    public ToolRegistry(McpToolGateway mcpGateway) {
        this.mcpGateway = mcpGateway;
    }

    public void authorize(SubAgent agent, java.util.Set<String> approvedTools) {
        for (String toolName : agent.requiredTools()) {
            ToolDescriptor tool = tools.get(toolName);
            if (tool == null) throw new IllegalStateException("Agent 请求了未注册工具: " + toolName);
            if (tool.risk() == ToolRisk.HIGH_RISK && !approvedTools.contains(toolName)) {
                throw new ApprovalRequiredException(agent.name(), toolName);
            }
        }
    }

    public List<ToolDescriptor> all() {
        List<ToolDescriptor> result = new ArrayList<>(tools.values());
        if (mcpGateway != null) {
            mcpGateway.tools().forEach(tool -> result.add(new ToolDescriptor(tool.exposedName(),
                    tool.description() == null ? "MCP 外部工具" : tool.description(), ToolRisk.EXTERNAL_CALL)));
        }
        return result.stream().sorted(Comparator.comparing(ToolDescriptor::name)).toList();
    }

    public void authorizeTool(String toolName, Set<String> approvedTools) {
        ToolDescriptor tool = tools.get(toolName);
        if (tool == null && mcpGateway != null) {
            tool = all().stream().filter(item -> item.name().equals(toolName)).findFirst().orElse(null);
        }
        if (tool == null) throw new IllegalStateException("工具未注册或当前不可用: " + toolName);
        if (tool.risk() == ToolRisk.HIGH_RISK && !approvedTools.contains(toolName)) {
            throw new ApprovalRequiredException("react-supervisor", toolName);
        }
    }

    public Object invoke(String toolName, Map<String, Object> arguments) {
        if (mcpGateway == null || !toolName.startsWith("mcp:")) {
            throw new IllegalArgumentException("本地工具不支持模型直接调用: " + toolName);
        }
        authorizeTool(toolName, Set.of());
        return mcpGateway.invoke(toolName, arguments);
    }

    public static class ApprovalRequiredException extends RuntimeException {
        private final String tool;

        public ApprovalRequiredException(String agent, String tool) {
            super("Agent " + agent + " 使用高风险工具 " + tool + " 前需要审批");
            this.tool = tool;
        }

        public String tool() { return tool; }
    }
}
