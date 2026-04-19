package com.ddjonline.mcp.ipinfo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class IpInfoClientTest {

    private HttpServer server;
    private String baseUrl;

    private volatile int stubStatus = 200;
    private volatile String stubBody = "{}";
    private volatile String lastPath;
    private volatile String lastQuery;
    private volatile String lastMethod;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastQuery = exchange.getRequestURI().getQuery();
            lastMethod = exchange.getRequestMethod();
            var bytes = stubBody.getBytes();
            exchange.sendResponseHeaders(stubStatus, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // --- lookupIp internal IP validation ---

    @Test
    void lookupIp_loopbackIpv4_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("127.0.0.1"));
    }

    @Test
    void lookupIp_loopbackIpv6_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("::1"));
    }

    @Test
    void lookupIp_privateClass10_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("10.0.0.1"));
    }

    @Test
    void lookupIp_privateClass172_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("172.16.0.1"));
    }

    @Test
    void lookupIp_privateClass192168_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("192.168.1.1"));
    }

    @Test
    void lookupIp_linkLocalIpv4_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("169.254.0.1"));
    }

    @Test
    void lookupIp_linkLocalIpv6_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("fe80::1"));
    }

    @Test
    void lookupIp_anyLocalAddress_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("0.0.0.0"));
    }

    @Test
    void lookupIp_internalValidation_messageContainsIp() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("10.1.2.3"));
        assertTrue(ex.getMessage().contains("10.1.2.3"));
    }

    @Test
    void lookupIp_publicIp_doesNotThrowForInternalCheck() throws Exception {
        // Verifies public IPs pass validation and reach the HTTP layer
        stubBody = "{\"ip\":\"8.8.8.8\"}";
        assertDoesNotThrow(() -> new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8"));
    }

    // --- lookupIp URL construction ---

    @Test
    void lookupIp_withoutToken_usesCorrectPath() throws Exception {
        new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8");
        assertEquals("/8.8.8.8/json", lastPath);
        assertNull(lastQuery);
    }

    @Test
    void lookupIp_withToken_appendsTokenQueryParam() throws Exception {
        new IpInfoClient("mytoken", baseUrl).lookupIp("8.8.8.8");
        assertEquals("/8.8.8.8/json", lastPath);
        assertEquals("token=mytoken", lastQuery);
    }

    @Test
    void lookupIp_blankToken_treatedAsNoToken() throws Exception {
        new IpInfoClient("   ", baseUrl).lookupIp("1.1.1.1");
        assertEquals("/1.1.1.1/json", lastPath);
        assertNull(lastQuery);
    }

    @Test
    void lookupIp_emptyToken_treatedAsNoToken() throws Exception {
        new IpInfoClient("", baseUrl).lookupIp("1.1.1.1");
        assertEquals("/1.1.1.1/json", lastPath);
        assertNull(lastQuery);
    }

    // --- lookupIp response handling ---

    @Test
    void lookupIp_success_returnsResponseBody() throws Exception {
        stubBody = "{\"ip\":\"8.8.8.8\",\"city\":\"Mountain View\",\"country\":\"US\"}";
        assertEquals(stubBody, new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8"));
    }

    @Test
    void lookupIp_http404_throwsRuntimeException() throws Exception {
        stubStatus = 404;
        stubBody = "Not Found";
        var ex = assertThrows(RuntimeException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("8.8.4.4"));
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void lookupIp_http429_includesBodyInMessage() throws Exception {
        stubStatus = 429;
        stubBody = "Rate limit exceeded";
        var ex = assertThrows(RuntimeException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8"));
        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
    }

    @Test
    void lookupIp_http500_throwsRuntimeException() throws Exception {
        stubStatus = 500;
        stubBody = "Internal Server Error";
        var ex = assertThrows(RuntimeException.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8"));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void lookupIp_networkError_propagatesException() throws Exception {
        server.stop(0);
        assertThrows(Exception.class,
                () -> new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8"));
    }

    // --- getMyIp URL construction ---

    @Test
    void getMyIp_withoutToken_usesRootJsonPath() throws Exception {
        new IpInfoClient(null, baseUrl).getMyIp();
        assertEquals("/json", lastPath);
        assertNull(lastQuery);
    }

    @Test
    void getMyIp_withToken_appendsTokenQueryParam() throws Exception {
        new IpInfoClient("testtoken", baseUrl).getMyIp();
        assertEquals("/json", lastPath);
        assertEquals("token=testtoken", lastQuery);
    }

    // --- getMyIp response handling ---

    @Test
    void getMyIp_success_returnsResponseBody() throws Exception {
        stubBody = "{\"ip\":\"1.2.3.4\",\"country\":\"US\"}";
        assertEquals(stubBody, new IpInfoClient(null, baseUrl).getMyIp());
    }

    @Test
    void getMyIp_http401_throwsRuntimeException() throws Exception {
        stubStatus = 401;
        stubBody = "Unauthorized";
        var ex = assertThrows(RuntimeException.class,
                () -> new IpInfoClient("badtoken", baseUrl).getMyIp());
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void getMyIp_networkError_propagatesException() throws Exception {
        server.stop(0);
        assertThrows(Exception.class,
                () -> new IpInfoClient(null, baseUrl).getMyIp());
    }

    // --- request method ---

    @Test
    void lookupIp_usesGetMethod() throws Exception {
        new IpInfoClient(null, baseUrl).lookupIp("8.8.8.8");
        assertEquals("GET", lastMethod);
    }

    @Test
    void getMyIp_usesGetMethod() throws Exception {
        new IpInfoClient(null, baseUrl).getMyIp();
        assertEquals("GET", lastMethod);
    }
}
