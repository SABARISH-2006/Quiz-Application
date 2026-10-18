package com.quizapp.config;

import com.quizapp.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("user") : null;

        if (uri.startsWith("/api/student")) {
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in.");
                return false;
            }
            if (!"STUDENT".equals(user.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Student access required.");
                return false;
            }
        } else if (uri.startsWith("/api/teacher")) {
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Please log in.");
                return false;
            }
            if (!"TEACHER".equals(user.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Teacher access required.");
                return false;
            }
        }

        return true;
    }
}
