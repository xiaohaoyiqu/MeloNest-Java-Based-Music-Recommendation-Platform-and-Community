package com.haoran.music.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

   
                                                                
  
                      
   
@Slf4j
@Service
public class ElasticsearchHttpClient {

    @Value("${search.elasticsearch.uris:http://127.0.0.1:9200}")
    private String uriConfig;

    @Value("${search.elasticsearch.username:}")
    private String username;

    @Value("${search.elasticsearch.password:}")
    private String password;

    @Value("${search.elasticsearch.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${search.elasticsearch.socket-timeout-ms:2000}")
    private int socketTimeoutMs;

    @Resource
    private ObjectMapper objectMapper;

    private RestTemplate restTemplate;
    private List<String> endpoints;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(100, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(100, socketTimeoutMs));
        restTemplate = new RestTemplate(requestFactory);
        endpoints = parseEndpoints(uriConfig);
    }

    public String request(HttpMethod method, String path, Object body) {
        return request(method, path, body, MediaType.APPLICATION_JSON);
    }

    public String request(HttpMethod method, String path, Object body, MediaType contentType) {
        if (endpoints.isEmpty()) {
            throw new ElasticsearchRequestException("Elasticsearch URI 未配置");
        }

        String endpoint = endpoints.get(0) + normalizePath(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(utf8MediaType(contentType));
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        if (isNotBlank(username)) {
            String token = username + ":" + (password == null ? "" : password);
            headers.set("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }

        try {
            String payload = body == null ? null
                    : body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, method, new HttpEntity<>(payload, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ElasticsearchRequestException(
                        "Elasticsearch HTTP " + response.getStatusCodeValue() + ": " + response.getBody());
            }
            return response.getBody();
        } catch (ElasticsearchRequestException e) {
            throw e;
        } catch (RestClientException | java.io.IOException e) {
            throw new ElasticsearchRequestException("Elasticsearch 请求失败: " + endpoint, e);
        }
    }

    public boolean isAvailable() {
        try {
            request(HttpMethod.GET, "/", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> parseEndpoints(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String item : value.split(",")) {
            String endpoint = item == null ? "" : item.trim();
            while (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }
            if (!endpoint.isEmpty()) {
                result.add(endpoint);
            }
        }
        return result;
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty() || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    MediaType utf8MediaType(MediaType contentType) {
        return new MediaType(contentType, StandardCharsets.UTF_8);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class ElasticsearchRequestException extends RuntimeException {
        public ElasticsearchRequestException(String message) {
            super(message);
        }

        public ElasticsearchRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
