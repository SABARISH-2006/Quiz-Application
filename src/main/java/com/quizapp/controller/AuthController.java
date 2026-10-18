package com.quizapp.controller;

import com.quizapp.model.User;
import com.quizapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Username is already taken."));
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Email is already in use."));
        }
        if (user.getRole() == null || (!user.getRole().equals("STUDENT") && !user.getRole().equals("TEACHER"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid role specified."));
        }

        User savedUser = userRepository.save(user);
        savedUser.setPassword(null); // Don't return password
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password."));
        }

        User user = userOpt.get();
        HttpSession session = request.getSession(true);
        session.setAttribute("user", user);

        User responseUser = new User();
        responseUser.setId(user.getId());
        responseUser.setName(user.getName());
        responseUser.setEmail(user.getEmail());
        responseUser.setUsername(user.getUsername());
        responseUser.setRole(user.getRole());

        return ResponseEntity.ok(responseUser);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not logged in."));
        }
        User sessionUser = (User) session.getAttribute("user");
        Optional<User> userOpt = userRepository.findById(sessionUser.getId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found."));
        }
        User user = userOpt.get();
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> updates, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not logged in."));
        }
        User sessionUser = (User) session.getAttribute("user");
        Optional<User> userOpt = userRepository.findById(sessionUser.getId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found."));
        }

        User user = userOpt.get();
        
        String name = updates.get("name");
        String email = updates.get("email");
        String password = updates.get("password");

        if (name != null && !name.trim().isEmpty()) {
            user.setName(name);
        }
        if (email != null && !email.trim().isEmpty()) {
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Email is already in use."));
            }
            user.setEmail(email);
        }
        if (password != null && !password.trim().isEmpty()) {
            user.setPassword(password);
        }

        User updatedUser = userRepository.save(user);
        session.setAttribute("user", updatedUser);

        updatedUser.setPassword(null);
        return ResponseEntity.ok(updatedUser);
    }
}
