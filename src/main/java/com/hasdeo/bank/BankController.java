package com.hasdeo.bank;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BankController {
    private static final String DEMO_USER = "alok2701";
    private static final String DEMO_PASSWORD = "hps2701";
    private static double balance = 25000.00;
    private static final List<Map<String, Object>> transactions = new ArrayList<>();

    static {
        addTransaction("Account opened", 25000.00, "CREDIT");
    }

    private static void addTransaction(String description, double amount, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "HB" + System.currentTimeMillis());
        item.put("description", description);
        item.put("amount", amount);
        item.put("type", type);
        item.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));
        transactions.add(0, item);
    }

    private void requireLogin(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (!DEMO_USER.equals(username) || !DEMO_PASSWORD.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect username or password.");
        }
        session.setAttribute("loggedIn", true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", "HASDEO BANK Admin");
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true);
    }

    @GetMapping("/account")
    public Map<String, Object> account(HttpSession session) {
        requireLogin(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "HASDEO BANK Admin");
        result.put("accountNumber", "**** **** 2701");
        result.put("accountType", "Premium Savings");
        result.put("balance", balance);
        result.put("transactions", new ArrayList<>(transactions));
        return result;
    }

    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody Map<String, Object> body, HttpSession session) {
        requireLogin(session);
        double amount = getAmount(body);
        if (amount <= 0 || amount > 1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid amount.");
        synchronized (BankController.class) {
            balance += amount;
            addTransaction("Cash deposit", amount, "CREDIT");
        }
        return Map.of("success", true, "balance", balance);
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body, HttpSession session) {
        requireLogin(session);
        double amount = getAmount(body);
        if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid amount.");
        synchronized (BankController.class) {
            if (amount > balance) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient demo balance.");
            balance -= amount;
            addTransaction("Cash withdrawal", amount, "DEBIT");
        }
        return Map.of("success", true, "balance", balance);
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody Map<String, Object> body, HttpSession session) {
        requireLogin(session);
        double amount = getAmount(body);
        String recipient = String.valueOf(body.getOrDefault("recipient", "")).trim();
        if (recipient.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter recipient name or account label.");
        if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid amount.");
        synchronized (BankController.class) {
            if (amount > balance) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient demo balance.");
            balance -= amount;
            addTransaction("Demo transfer to " + recipient, amount, "DEBIT");
        }
        return Map.of("success", true, "balance", balance);
    }

    private double getAmount(Map<String, Object> body) {
        try { return Double.parseDouble(String.valueOf(body.get("amount"))); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid amount."); }
    }
}
