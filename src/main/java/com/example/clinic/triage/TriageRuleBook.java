package com.example.clinic.triage;

import com.example.clinic.triage.rules.TriageRuleLoader;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TriageRuleBook {

    static final String RULES_PATH = "triage/triage-rules.json";

    private final TriageRules rules;

    public TriageRuleBook() {
        try (InputStream in = new ClassPathResource(RULES_PATH).getInputStream()) {
            this.rules = TriageRuleLoader.parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("Không mở được " + RULES_PATH, e);
        }
    }

    public TriageRules rules() {
        return rules;
    }
}
