package com.hasdeo.bank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SupportAiService {
    private final RestClient client = RestClient.builder().baseUrl("https://api.openai.com/v1").build();
    private final ObjectMapper mapper;

    @Value("${OPENAI_API_KEY:}") private String apiKey;
    @Value("${OPENAI_MODEL:gpt-4.1-mini}") private String model;

    private static final String INSTRUCTIONS = """
        You are the customer-help assistant for HASDEO BANK, a student-built EDUCATIONAL DEMO website, not a real bank.
        Answer in simple, polite English or Hinglish matching the customer's language. Only explain this website's demo features.
        Website guide: visitors can create a demo customer account using a username of 4-40 characters and a password of at least 8 characters. The site creates a fictional 16-digit demo card number and a separate 4-digit transfer code. Customers sign in using their own username/password. Their dashboard shows demo balance, account details, deposit, withdrawal, demo transfers using the recipient's 4-digit code, and transaction history. Transfers are only between demo customer accounts. Balances refresh automatically about every 4 seconds. The session is intended to persist across page refreshes and expires after about 30 minutes of inactivity. The public Contact Support form saves a ticket and emails the site owner when Gmail notifications are configured. If the assistant cannot confidently solve a problem, ask the customer for a short description and offer human support; set needsHuman=true.
        The owner/admin area is private. Never reveal, guess, or discuss admin login credentials, secret environment variables, other customers' private details, or internal account records. Never ask for passwords, PINs, OTPs, full card numbers, or other secrets. Do not claim to perform account actions, transfers, refunds, or change account data. Do not request real banking details. If a user reports money loss, fraud, or a real-bank issue, clearly state this demo cannot handle real banking and tell them to contact their actual bank through its official channel.
        Return ONLY valid JSON with exactly two fields: {\"answer\":\"customer-facing reply\",\"needsHuman\":true_or_false}. Set needsHuman=true for login/account-specific problems, errors you cannot resolve from this guide, requests to change private data, safety/security incidents, or when uncertain. Keep answers concise and actionable.
        """;

    public SupportAiService(ObjectMapper mapper) { this.mapper = mapper; }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    public AiReply reply(List<Map<String, String>> history, String message) {
        if (!isConfigured()) throw new IllegalStateException("AI support is not configured yet.");
        StringBuilder conversation = new StringBuilder();
        if (history != null) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> item = history.get(i);
                String role = item.getOrDefault("role", "");
                String content = item.getOrDefault("content", "");
                if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    conversation.append(role.equals("user") ? "Customer: " : "Assistant: ")
                            .append(content, 0, Math.min(content.length(), 1200)).append('\n');
                }
            }
        }
        conversation.append("Customer: ").append(message);
        try {
            Map<String, Object> payload = Map.of("model", model, "instructions", INSTRUCTIONS,
                    "input", conversation.toString(), "max_output_tokens", 450, "store", false,
                    "text", Map.of("format", Map.of("type", "json_object")));
            String raw = client.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(String.class);
            JsonNode root = mapper.readTree(raw);
            String output = extractOutputText(root);
            JsonNode parsed = mapper.readTree(output);
            String answer = parsed.path("answer").asText("").trim();
            boolean needsHuman = parsed.path("needsHuman").asBoolean(true);
            if (answer.isBlank()) throw new IllegalStateException("AI returned an empty reply.");
            return new AiReply(answer, needsHuman);
        } catch (Exception ex) {
            throw new IllegalStateException("AI support could not complete this request.", ex);
        }
    }

    private String extractOutputText(JsonNode root) {
        StringBuilder text = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) for (JsonNode item : output) {
            if ("message".equals(item.path("type").asText())) {
                JsonNode content = item.path("content");
                if (content.isArray()) for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText())) text.append(part.path("text").asText(""));
                }
            }
        }
        String result = text.toString().trim();
        if (result.isBlank()) throw new IllegalStateException("AI response contained no text.");
        return result;
    }

    public record AiReply(String answer, boolean needsHuman) {}
}
