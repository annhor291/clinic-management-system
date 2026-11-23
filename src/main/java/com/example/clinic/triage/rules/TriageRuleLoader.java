package com.example.clinic.triage.rules;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Đọc + kiểm tra bộ luật. Tách khỏi Spring để unit test không cần khởi động context.
public final class TriageRuleLoader {

    // Cố ý bật FAIL_ON_UNKNOWN_PROPERTIES: gõ sai tên trường trong file luật phải báo lỗi, không được âm thầm bỏ qua
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TriageRuleLoader() {}

    public static TriageRules parse(InputStream in) {
        TriageRules rules;
        try {
            rules = MAPPER.readValue(in, TriageRules.class);
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được file luật triage: " + e.getMessage(), e);
        }
        if (rules == null) {
            throw new IllegalStateException("File luật triage rỗng");
        }
        TriageRulesValidator.validate(rules);
        return rules;
    }

    public static TriageRules parse(String json) {
        return parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
