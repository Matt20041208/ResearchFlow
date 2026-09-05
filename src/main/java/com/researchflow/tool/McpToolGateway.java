package com.researchflow.tool;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpToolGateway {
    private final ObjectProvider<McpSyncClient> clients;

    public McpToolGateway(ObjectProvider<McpSyncClient> clients) {
        this.clients = clients;
    }

    public List<RemoteTool> tools() {
        List<RemoteTool> result = new ArrayList<>();
        clients.orderedStream().forEach(client -> {
            try {
                client.listTools().tools().forEach(tool -> result.add(new RemoteTool(
                        exposedName(tool.name()), tool.name(), tool.description(), client)));
            } catch (RuntimeException ignored) {
                // An optional disconnected MCP server must not break local research.
            }
        });
        return result;
    }

    public Object invoke(String exposedName, Map<String, Object> arguments) {
        RemoteTool remote = tools().stream()
                .filter(tool -> tool.exposedName().equals(exposedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MCP 工具不存在或当前不可用: " + exposedName));
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        McpSchema.CallToolResult result = remote.client().callTool(
                new McpSchema.CallToolRequest(remote.actualName(), safeArguments));
        if (result.isError()) throw new IllegalStateException("MCP 工具执行失败: " + exposedName);
        return result.content();
    }

    private String exposedName(String actualName) {
        return "mcp:" + actualName;
    }

    public record RemoteTool(String exposedName, String actualName, String description, McpSyncClient client) {
    }
}
