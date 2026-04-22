package com.ddjonline.mcp.ipinfo;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import tools.jackson.databind.json.JsonMapper;

public class IpInfoMcpServer {

    public static void main(String[] args) {
        var token = System.getenv("IPINFO_TOKEN");
        var client = new IpInfoClient(token);
        var jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        var server = buildServer(transportProvider, jsonMapper, client);
        try {
            // Keep the application running to handle requests from stdin
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static McpSyncServer buildServer(McpServerTransportProvider transportProvider, McpJsonMapper jsonMapper, IpInfoClient client) {
        var lookupIpSpec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("lookup_ip")
                        .description("Look up geolocation and network information for a specific IP address. " +
                                "Returns city, region, country, coordinates, ISP/org, postal code, and timezone.")
                        .inputSchema(jsonMapper, """
                                {
                                  "type": "object",
                                  "properties": {
                                    "ip": {
                                      "type": "string",
                                      "description": "IPv4 or IPv6 address to look up"
                                    }
                                  },
                                  "required": ["ip"]
                                }
                                """)
                        .build())
                .callHandler((exchange, request) -> {
                    var ip = (String) request.arguments().get("ip");
                    try {
                        var result = client.lookupIp(ip);
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(result)
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder()
                                .addTextContent("Error: " + e.getMessage())
                                .isError(true)
                                .build();
                    }
                })
                .build();

        var getMyIpSpec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name("get_my_ip")
                        .description("Get geolocation and network information for the current machine's public IP address.")
                        .inputSchema(jsonMapper, """
                                {
                                  "type": "object",
                                  "properties": {}
                                }
                                """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var result = client.getMyIp();
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(result)
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        return McpSchema.CallToolResult.builder()
                                .addTextContent("Error: " + e.getMessage())
                                .isError(true)
                                .build();
                    }
                })
                .build();

        return McpServer.sync(transportProvider)
                .serverInfo("mcp-ipinfo-java", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(lookupIpSpec, getMyIpSpec)
                .build();
    }
}
