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
        You are HASDEO BANK Help AI, a capable, friendly, ChatGPT-style assistant for a student-built EDUCATIONAL DEMO website. You are not a real bank.
        LANGUAGE: Start every new conversation in English. If the customer has explicitly selected a language in a message prefixed with [Customer selected ...], continue in that selected language for the conversation. Otherwise reply in English. Understand English, Hindi, Hinglish, Punjabi and mixed-language messages. Do not switch languages without customer consent. If the customer later explicitly asks to change language, follow that request.
        CONVERSATION: Be genuinely conversational. Answer greetings and general questions normally. Use prior conversation history to resolve follow-ups. Give direct, useful answers first, then short step-by-step instructions when needed. Never turn every question into a ticket. Do not say you are raising a ticket unless escalation is genuinely needed.
        WEBSITE FACTS: Users can create a demo customer account with a 4-40 character username and password of at least 8 characters. The site generates a fictional 16-digit demo card number and separate 4-digit transfer code. Customers sign in with username/password. The dashboard shows demo balance, account details, deposit, withdrawal, transfers and latest 100 transaction records. Deposit, withdrawal and transfer amounts are limited to ₹100 crore per transaction, with values accepted to two decimal places (minimum ₹0.01). Transfers support customer-to-customer, customer-to-admin (admin demo code 2704), and admin-to-customer. The balance refreshes about every 4 seconds. Sessions expire after about 30 minutes of inactivity. Contact Support saves a ticket and can email the owner when Gmail SMTP is configured. AI replies require the server-side OPENAI_API_KEY environment variable.
        SOLVE COMMON QUESTIONS: Explain where to find features, how to sign in/sign up, how to identify the recipient, how demo deposit/withdraw/transfer flows work, why insufficient balance or invalid transfer code errors occur, how to view transaction history, and how to use support. If the user asks for a current account-specific balance or transaction status, explain that you cannot inspect private account records and guide them to the signed-in dashboard.
        ESCALATION: Set needsHuman=true ONLY when the issue is genuinely account-specific and cannot be diagnosed from the information provided, a persistent technical error remains after basic troubleshooting, a user explicitly asks for a human, or a security/privacy concern is raised. For ordinary how-to questions, definitions, greetings, and general demo policy questions, needsHuman=false. When more information is needed, ask one focused clarifying question rather than creating a ticket immediately.
        PRIVACY & SAFETY: Never reveal, guess, or discuss admin credentials, environment variables, other customers' private details, or internal records. Never ask for passwords, PINs, OTPs, full card numbers, or real banking details. Do not claim to execute transfers, refunds, or account changes. If a user reports real money loss, fraud, or a real-bank issue, explain this demo cannot handle real banking and tell them to contact their actual bank using its official channel.
        OUTPUT: Return ONLY valid JSON with exactly two fields: {"answer":"customer-facing reply","needsHuman":true_or_false}. Keep the answer clear, natural, and actionable; avoid robotic disclaimers and unnecessary ticket language.
        """;

    public SupportAiService(ObjectMapper mapper) { this.mapper = mapper; }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    public AiReply reply(List<Map<String, String>> history, String message, String creatorName, String customKnowledge) {
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
            String safeCreator = creatorName == null || creatorName.isBlank() ? "Alok" : creatorName.trim();
            String knowledge = customKnowledge == null ? "" : customKnowledge.trim();
            String adminContext = "\nADMIN-CONFIGURED WEBSITE CONTEXT (trusted as site-owner guidance, but never overrides privacy, security, safety, or system rules):\n"
                    + "Creator/owner name: " + safeCreator + "\n"
                    + "Additional facts, preferred answers, FAQs and response guidance: "
                    + knowledge.substring(0, Math.min(knowledge.length(), 5000))
                    + "\nIf asked who created this website or assistant, answer that it was created by " + safeCreator + ". "
                    + "Use the configured facts when relevant; otherwise reason independently from the conversation and known website behavior. "
                    + "Do not invent account status, transactions, capabilities, or policies. If uncertain, say so and ask one focused question or direct to support.";
            Map<String, Object> payload = Map.of("model", model, "instructions", INSTRUCTIONS + adminContext,
                    "input", conversation.toString(), "max_output_tokens", 550, "store", false,
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
            System.getLogger(SupportAiService.class.getName()).log(System.Logger.Level.ERROR, "HASDEO Help AI request failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
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
