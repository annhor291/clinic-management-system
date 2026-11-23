package com.example.clinic.payment.gateway;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// Lớp mỏng gọi HTTP sang VNPay. Tách riêng để test thay bằng mock, không cần mạng.
@Component
public class VnPayApiClient {

    private final RestClient restClient;

    public VnPayApiClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, String> postJson(String url, Map<String, String> body) {
        Map<String, Object> raw = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, String> result = new HashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> result.put(k, v == null ? null : String.valueOf(v)));
        }
        return result;
    }
}
