package com.hasdeo.bank.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminMailService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z").withZone(IST);

    private final JavaMailSender sender;
    @Value("${ADMIN_ALERT_EMAIL:alokpandey27048@gmail.com}") private String recipient;
    @Value("${spring.mail.username:}") private String from;
    @Value("${spring.mail.password:}") private String password;

    public AdminMailService(JavaMailSender sender) { this.sender = sender; }

    public boolean isConfigured() {
        return from != null && !from.isBlank() && password != null && !password.isBlank()
                && recipient != null && !recipient.isBlank();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", isConfigured());
        result.put("sender", from == null ? "" : from);
        result.put("recipient", recipient == null ? "" : recipient);
        result.put("message", isConfigured() ? "Gmail SMTP credentials are configured." :
                "Set MAIL_USERNAME and MAIL_APP_PASSWORD in the hosting environment; set ADMIN_ALERT_EMAIL for the recipient.");
        return result;
    }

    public Map<String, Object> sendTestNotification() {
        if (!isConfigured()) throw new IllegalStateException("Gmail is not configured. Add MAIL_USERNAME and MAIL_APP_PASSWORD to environment variables.");
        send("HASDEO BANK Gmail test", "This is a test notification from the HASDEO BANK educational demo.\n\nTime (IST): " + IST_FORMAT.format(Instant.now()) + "\n\nNo real banking or payment activity is involved.");
        return Map.of("success", true, "message", "Test email sent to " + recipient + ". Check the inbox and spam folder.");
    }

    public boolean notifyAdmin(String subject, String details) {
        if (!isConfigured()) return false;
        String body = details + "\n\nTime (IST): " + IST_FORMAT.format(Instant.now())
                + "\n\nEducational demo only — no real money or payment network.";
        try { send("HASDEO BANK Demo: " + subject, body); }
        catch (Exception ex) { System.err.println("Admin email notification could not be sent: " + ex.getMessage()); return false; }
        return true;
    }

    private void send(String subject, String body) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(recipient);
        mail.setSubject(subject);
        mail.setText(body);
        mail.setFrom(from);
        sender.send(mail);
    }
}
