package com.ddjonline.mcp.ipinfo;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpInfoMcpServerTest {

    @Mock
    IpInfoClient mockClient;

    private McpSyncServer server;
    private PipedOutputStream clientOut;
    private PipedInputStream clientPipedIn;
    private BufferedReader clientIn;
    private JsonMapper jackson;

    @BeforeEach
    void setUp() throws Exception {
        var serverIn = new PipedInputStream();
        clientOut = new PipedOutputStream(serverIn);

        var serverOut = new PipedOutputStream();
        clientPipedIn = new PipedInputStream(serverOut);
        clientIn = new BufferedReader(new InputStreamReader(clientPipedIn));

        jackson = JsonMapper.builder().build();
        var jsonMapper = new JacksonMcpJsonMapper(jackson);
        var transport = new StdioServerTransportProvider(jsonMapper, serverIn, serverOut);

        server = IpInfoMcpServer.buildServer(transport, jsonMapper, mockClient);
        Thread.sleep(100); // allow Reactor to subscribe to the input stream
    }

    @AfterEach
    void tearDown() throws Exception {
        clientOut.close();
        clientIn.close();
        if (server != null) server.close();
    }

    // --- initialization ---

    @Test
    void initialize_returnsCorrectServerName() throws Exception {
        send(initMsg(1));
        var node = readResponse();
        assertEquals("mcp-ipinfo-java", node.path("result").path("serverInfo").path("name").asString());
    }

    @Test
    void initialize_returnsCorrectServerVersion() throws Exception {
        send(initMsg(1));
        var node = readResponse();
        assertEquals("1.0.0", node.path("result").path("serverInfo").path("version").asString());
    }

    @Test
    void initialize_capabilitiesIncludeTools() throws Exception {
        send(initMsg(1));
        var node = readResponse();
        assertFalse(node.path("result").path("capabilities").path("tools").isMissingNode());
    }

    @Test
    void initialize_protocolVersionEchoed() throws Exception {
        send(initMsg(1));
        var node = readResponse();
        var version = node.path("result").path("protocolVersion").asString();
        assertNotNull(version);
        assertFalse(version.isBlank());
    }

    // --- tools/list ---

    @Test
    void toolsList_returnsExactlyTwoTools() throws Exception {
        handshake();
        send(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        var node = readResponse();
        assertEquals(2, node.path("result").path("tools").size());
    }

    @Test
    void toolsList_containsLookupIpTool() throws Exception {
        handshake();
        send(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        var tools = readResponse().path("result").path("tools");
        assertTrue(toolNames(tools).contains("lookup_ip"));
    }

    @Test
    void toolsList_containsGetMyIpTool() throws Exception {
        handshake();
        send(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        var tools = readResponse().path("result").path("tools");
        assertTrue(toolNames(tools).contains("get_my_ip"));
    }

    @Test
    void toolsList_lookupIp_hasIpPropertyInSchema() throws Exception {
        handshake();
        send(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        var tools = readResponse().path("result").path("tools");
        var lookupIp = findTool(tools, "lookup_ip");
        assertFalse(lookupIp.path("inputSchema").path("properties").path("ip").isMissingNode());
    }

    @Test
    void toolsList_lookupIp_ipIsRequired() throws Exception {
        handshake();
        send(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        var tools = readResponse().path("result").path("tools");
        var schema = findTool(tools, "lookup_ip").path("inputSchema");
        var required = schema.path("required");
        assertTrue(required.isArray());
        assertEquals("ip", required.get(0).asString());
    }

    // --- tools/call lookup_ip ---

    @Test
    void lookupIp_success_isErrorFalse() throws Exception {
        when(mockClient.lookupIp("8.8.8.8")).thenReturn("{\"ip\":\"8.8.8.8\"}");
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "8.8.8.8")));
        var result = readResponse().path("result");
        assertFalse(result.path("isError").asBoolean());
    }

    @Test
    void lookupIp_success_contentContainsIpData() throws Exception {
        var ipJson = "{\"ip\":\"8.8.8.8\",\"city\":\"Mountain View\",\"country\":\"US\"}";
        when(mockClient.lookupIp("8.8.8.8")).thenReturn(ipJson);
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "8.8.8.8")));
        var text = firstContentText(readResponse());
        assertTrue(text.contains("8.8.8.8"));
        assertTrue(text.contains("Mountain View"));
    }

    @Test
    void lookupIp_success_contentTypeIsText() throws Exception {
        when(mockClient.lookupIp("8.8.8.8")).thenReturn("{\"ip\":\"8.8.8.8\"}");
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "8.8.8.8")));
        var contentType = readResponse().path("result").path("content").get(0).path("type").asString();
        assertEquals("text", contentType);
    }

    @Test
    void lookupIp_clientThrows_isErrorTrue() throws Exception {
        when(mockClient.lookupIp(any())).thenThrow(new RuntimeException("API unreachable"));
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "8.8.8.8")));
        var result = readResponse().path("result");
        assertTrue(result.path("isError").asBoolean());
    }

    @Test
    void lookupIp_clientThrows_errorMessageInContent() throws Exception {
        when(mockClient.lookupIp(any())).thenThrow(new RuntimeException("rate limit exceeded"));
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "8.8.8.8")));
        assertTrue(firstContentText(readResponse()).contains("rate limit exceeded"));
    }

    @Test
    void lookupIp_forwardsIpArgumentToClient() throws Exception {
        when(mockClient.lookupIp("1.1.1.1")).thenReturn("{\"ip\":\"1.1.1.1\"}");
        handshake();
        send(toolCall(3, "lookup_ip", Map.of("ip", "1.1.1.1")));
        var text = firstContentText(readResponse());
        assertTrue(text.contains("1.1.1.1"));
    }

    // --- tools/call get_my_ip ---

    @Test
    void getMyIp_success_isErrorFalse() throws Exception {
        when(mockClient.getMyIp()).thenReturn("{\"ip\":\"1.2.3.4\"}");
        handshake();
        send(toolCall(4, "get_my_ip", Map.of()));
        assertFalse(readResponse().path("result").path("isError").asBoolean());
    }

    @Test
    void getMyIp_success_contentContainsIpData() throws Exception {
        when(mockClient.getMyIp()).thenReturn("{\"ip\":\"1.2.3.4\",\"country\":\"US\"}");
        handshake();
        send(toolCall(4, "get_my_ip", Map.of()));
        var text = firstContentText(readResponse());
        assertTrue(text.contains("1.2.3.4"));
    }

    @Test
    void getMyIp_clientThrows_isErrorTrue() throws Exception {
        when(mockClient.getMyIp()).thenThrow(new RuntimeException("Network error"));
        handshake();
        send(toolCall(4, "get_my_ip", Map.of()));
        assertTrue(readResponse().path("result").path("isError").asBoolean());
    }

    @Test
    void getMyIp_clientThrows_errorMessageInContent() throws Exception {
        when(mockClient.getMyIp()).thenThrow(new RuntimeException("timeout"));
        handshake();
        send(toolCall(4, "get_my_ip", Map.of()));
        assertTrue(firstContentText(readResponse()).contains("timeout"));
    }

    // --- helpers ---

    private void handshake() throws Exception {
        send(initMsg(1));
        readResponse();
        send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
    }

    private void send(Object msg) throws Exception {
        var line = jackson.writeValueAsString(msg) + "\n";
        clientOut.write(line.getBytes());
        clientOut.flush();
    }

    private JsonNode readResponse() throws Exception {
        var line = CompletableFuture
                .supplyAsync(() -> {
                    try { return clientIn.readLine(); }
                    catch (IOException e) { throw new RuntimeException(e); }
                })
                .get(5, TimeUnit.SECONDS);
        assertNotNull(line, "Server did not respond within 5 seconds");
        return jackson.readTree(line);
    }

    private String firstContentText(JsonNode response) {
        return response.path("result").path("content").get(0).path("text").asString();
    }

    private java.util.Set<String> toolNames(JsonNode tools) {
        var names = new java.util.HashSet<String>();
        for (var tool : tools) names.add(tool.path("name").asString());
        return names;
    }

    private JsonNode findTool(JsonNode tools, String name) {
        for (var tool : tools) {
            if (name.equals(tool.path("name").asString())) return tool;
        }
        throw new AssertionError("Tool not found: " + name);
    }

    private Map<String, Object> initMsg(int id) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "test", "version", "1.0")
                )
        );
    }

    private Map<String, Object> toolCall(int id, String name, Map<String, Object> args) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", "tools/call",
                "params", Map.of("name", name, "arguments", args)
        );
    }
}
