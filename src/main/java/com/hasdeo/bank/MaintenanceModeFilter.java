package com.hasdeo.bank;

import com.hasdeo.bank.repository.SiteSettingsRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MaintenanceModeFilter extends OncePerRequestFilter {
    private final SiteSettingsRepository settingsRepository;
    public MaintenanceModeFilter(SiteSettingsRepository settingsRepository) { this.settingsRepository = settingsRepository; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String apiPrefix = request.getContextPath() + "/api/";
        if (!uri.startsWith(apiPrefix)) { chain.doFilter(request, response); return; }
        String path = uri.substring(apiPrefix.length());
        boolean maintenance = settingsRepository.findById(1L).map(s -> s.isMaintenanceEnabled()).orElse(false);
        if (!maintenance || path.equals("public/status") || path.equals("login")) { chain.doFilter(request, response); return; }

        HttpSession session = request.getSession(false);
        boolean admin = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"))
                && session.getAttribute("customerId") == null;
        if (admin) { chain.doFilter(request, response); return; }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"HASDEO BANK is in maintenance mode. Customer access is temporarily unavailable.\"}");
    }
}
