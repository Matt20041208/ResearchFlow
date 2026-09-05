package com.researchflow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.agent.ResearchPlan;
import com.researchflow.agent.SourceSearchAgent;
import com.researchflow.knowledge.KnowledgeService;
import com.researchflow.model.Citation;
import com.researchflow.model.TraceView;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "research-flow.mcp.server.enabled", havingValue = "true")
public class ResearchFlowMcpServerConfiguration {

    @Bean
    public HttpServletSseServerTransportProvider researchFlowMcpTransport(ObjectMapper objectMapper) {
        return HttpServletSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .baseUrl("/mcp")
                .messageEndpoint("/message")
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer researchFlowMcpServer(HttpServletSseServerTransportProvider transport,
                                               ObjectMapper objectMapper, KnowledgeService knowledgeService,
                                               WorkspaceService workspaceService, SourceSearchAgent sourceSearchAgent,
                                               ResearchTaskService taskService) {
        return McpServer.sync(transport)
                .serverInfo("researchflow", "0.1.0")
                .instructions("ResearchFlow 科研工具：论文检索、Workspace 知识检索、引用和执行链路查询")
                .objectMapper(objectMapper)
                .tools(
                        paperSearchTool(sourceSearchAgent),
                        knowledgeSearchTool(knowledgeService, workspaceService),
                        citationTool(taskService, workspaceService),
                        traceTool(taskService, workspaceService))
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> researchFlowMcpServlet(
            HttpServletSseServerTransportProvider transport) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(transport, "/mcp/*");
        registration.setName("researchFlowMcpTransport");
        registration.setLoadOnStartup(1);
        return registration;
    }

    private McpServerFeatures.SyncToolSpecification paperSearchTool(SourceSearchAgent sourceSearchAgent) {
        McpSchema.Tool definition = new McpSchema.Tool("search_papers", "检索 Crossref 论文元数据",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                        + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10}},"
                        + "\"required\":[\"query\"]}");
        return new McpServerFeatures.SyncToolSpecification(definition, (exchange, arguments) -> {
            try {
                String query = required(arguments, "query");
                int limit = boundedInt(arguments.get("limit"), 5, 1, 10);
                List<?> sources = sourceSearchAgent.execute(new ResearchPlan(query, query, "论文与证据"));
                return success(sources.stream().limit(limit).toList());
            } catch (RuntimeException exception) {
                return failure(exception.getMessage());
            }
        });
    }

    private McpServerFeatures.SyncToolSpecification knowledgeSearchTool(KnowledgeService knowledgeService,
                                                                         WorkspaceService workspaceService) {
        McpSchema.Tool definition = new McpSchema.Tool("search_workspace_knowledge", "检索指定 Workspace 的私有知识",
                """
                {"type":"object","properties":{"workspaceId":{"type":"string"},
                "userId":{"type":"string"},"query":{"type":"string"},
                "limit":{"type":"integer","minimum":1,"maximum":10}},
                "required":["workspaceId","userId","query"]}
                """);
        return new McpServerFeatures.SyncToolSpecification(definition, (exchange, arguments) -> {
            try {
                String workspaceId = required(arguments, "workspaceId");
                workspaceService.require(workspaceId, required(arguments, "userId"), WorkspaceRole.VIEWER);
                int limit = boundedInt(arguments.get("limit"), 5, 1, 10);
                return success(knowledgeService.search(workspaceId, required(arguments, "query"), limit));
            } catch (RuntimeException exception) {
                return failure(exception.getMessage());
            }
        });
    }

    private McpServerFeatures.SyncToolSpecification citationTool(ResearchTaskService taskService,
                                                                  WorkspaceService workspaceService) {
        McpSchema.Tool definition = new McpSchema.Tool("get_research_citations", "查询研究任务的来源引用",
                """
                {"type":"object","properties":{"taskId":{"type":"string"},
                "userId":{"type":"string"}},"required":["taskId","userId"]}
                """);
        return new McpServerFeatures.SyncToolSpecification(definition, (exchange, arguments) -> {
            try {
                authorizeTask(taskService, workspaceService, arguments);
                List<Citation> citations = taskService.citations(required(arguments, "taskId"));
                return success(citations);
            } catch (RuntimeException exception) {
                return failure(exception.getMessage());
            }
        });
    }

    private McpServerFeatures.SyncToolSpecification traceTool(ResearchTaskService taskService,
                                                               WorkspaceService workspaceService) {
        McpSchema.Tool definition = new McpSchema.Tool("get_research_trace", "查询研究任务的 DAG 和 Agent 执行链路",
                """
                {"type":"object","properties":{"taskId":{"type":"string"},
                "userId":{"type":"string"}},"required":["taskId","userId"]}
                """);
        return new McpServerFeatures.SyncToolSpecification(definition, (exchange, arguments) -> {
            try {
                authorizeTask(taskService, workspaceService, arguments);
                TraceView trace = taskService.trace(required(arguments, "taskId"));
                return success(trace);
            } catch (RuntimeException exception) {
                return failure(exception.getMessage());
            }
        });
    }

    private void authorizeTask(ResearchTaskService taskService, WorkspaceService workspaceService,
                               Map<String, Object> arguments) {
        String taskId = required(arguments, "taskId");
        workspaceService.require(taskService.workspaceId(taskId), required(arguments, "userId"), WorkspaceRole.VIEWER);
    }

    private String required(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("缺少参数: " + key);
        return value.toString().trim();
    }

    private int boundedInt(Object value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.toString())));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("参数必须是整数");
        }
    }

    private McpSchema.CallToolResult success(Object value) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(String.valueOf(value))
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult failure(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message == null ? "MCP 工具执行失败" : message)
                .isError(true)
                .build();
    }
}
