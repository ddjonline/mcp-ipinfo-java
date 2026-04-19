package com.ddjonline.mcp.ipinfo;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class IpInfoClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final String token;

    public IpInfoClient(String token) {
        this(token, "https://ipinfo.io");
    }

    IpInfoClient(String token, String baseUrl) {
        this.token = token;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String lookupIp(String ip) throws Exception {
        validateIp(ip);
        return fetch(buildUrl("/" + ip + "/json"));
    }

    private void validateIp(String ip) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + ip);
        }
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("Lookup of internal IP addresses is not permitted: " + ip);
        }
    }

    public String getMyIp() throws Exception {
        return fetch(buildUrl("/json"));
    }

    private String buildUrl(String path) {
        if (token != null && !token.isBlank()) {
            return baseUrl + path + "?token=" + token;
        }
        return baseUrl + path;
    }

    private String fetch(String url) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("IPInfo API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }
}
