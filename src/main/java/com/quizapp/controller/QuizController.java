package com.quizapp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.model.Question;
import com.quizapp.model.Quiz;
import com.quizapp.model.QuizAttempt;
import com.quizapp.model.User;
import com.quizapp.repository.QuizAttemptRepository;
import com.quizapp.repository.QuizRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class QuizController {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- TEACHER APIS ---

    @PostMapping("/api/teacher/quizzes")
    public ResponseEntity<?> createQuiz(@RequestBody Quiz quiz, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User creator = (User) session.getAttribute("user");

        if (quiz.getQuizCode() == null || quiz.getQuizCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Quiz code is required."));
        }
        if (quizRepository.existsByQuizCode(quiz.getQuizCode())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Quiz code already exists."));
        }
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Quiz must have at least one question."));
        }

        quiz.setCreator(creator);
        
        for (Question question : quiz.getQuestions()) {
            question.setQuiz(quiz);
        }

        Quiz savedQuiz = quizRepository.save(quiz);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQuiz);
    }

    @GetMapping("/api/teacher/quizzes")
    public ResponseEntity<?> getTeacherQuizzes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User creator = (User) session.getAttribute("user");

        List<Quiz> quizzes = quizRepository.findByCreator(creator);
        return ResponseEntity.ok(quizzes);
    }

    @GetMapping("/api/teacher/attempts")
    public ResponseEntity<?> getTeacherReports(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User creator = (User) session.getAttribute("user");

        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizCreatorOrderByDateTakenDesc(creator);
        List<Map<String, Object>> response = getAttemptReportList(attempts);
        return ResponseEntity.ok(response);
    }

    // --- STUDENT APIS ---

    @GetMapping("/api/student/quizzes/code/{quizCode}")
    public ResponseEntity<?> getQuizByCode(@PathVariable String quizCode) {
        Optional<Quiz> quizOpt = quizRepository.findByQuizCode(quizCode);
        if (quizOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Quiz not found with code: " + quizCode));
        }
        return ResponseEntity.ok(secureQuiz(quizOpt.get()));
    }

    @PostMapping("/api/student/quizzes/take")
    public ResponseEntity<?> takeQuiz(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User student = (User) session.getAttribute("user");

        String quizCode = (String) payload.get("quizCode");
        Object answersObj = payload.get("answers");
        Map<String, String> answers = new HashMap<>();
        if (answersObj instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    answers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }

        if (quizCode == null || answersObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing quizCode or answers."));
        }

        Optional<Quiz> quizOpt = quizRepository.findByQuizCode(quizCode);
        if (quizOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Quiz not found."));
        }

        Quiz quiz = quizOpt.get();
        List<Question> questions = quiz.getQuestions();
        int score = 0;
        int totalQuestions = questions.size();

        for (Question question : questions) {
            String questionIdStr = String.valueOf(question.getId());
            String submittedAns = answers.get(questionIdStr);
            if (submittedAns != null && submittedAns.equalsIgnoreCase(question.getCorrectOption())) {
                score++;
            }
        }

        double percentage = (totalQuestions > 0) ? ((double) score / totalQuestions) * 100 : 0.0;

        String answersJson = "";
        try {
            answersJson = objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            answersJson = "{}";
        }

        QuizAttempt attempt = new QuizAttempt(student, quiz, score, totalQuestions, percentage, answersJson);
        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        Map<String, Object> result = new HashMap<>();
        result.put("attemptId", savedAttempt.getId());
        result.put("score", score);
        result.put("totalQuestions", totalQuestions);
        result.put("percentage", percentage);
        
        List<Map<String, Object>> review = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("questionId", q.getId());
            rMap.put("questionText", q.getQuestionText());
            rMap.put("optionA", q.getOptionA());
            rMap.put("optionB", q.getOptionB());
            rMap.put("optionC", q.getOptionC());
            rMap.put("optionD", q.getOptionD());
            rMap.put("correctOption", q.getCorrectOption());
            rMap.put("submittedOption", answers.get(String.valueOf(q.getId())));
            review.add(rMap);
        }
        result.put("review", review);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/student/attempts")
    public ResponseEntity<?> getStudentAttempts(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User student = (User) session.getAttribute("user");

        List<QuizAttempt> attempts = quizAttemptRepository.findByStudentOrderByDateTakenDesc(student);
        List<Map<String, Object>> response = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attempt.getId());
            map.put("quizTitle", attempt.getQuiz().getTitle());
            map.put("quizCode", attempt.getQuiz().getQuizCode());
            map.put("score", attempt.getScore());
            map.put("totalQuestions", attempt.getTotalQuestions());
            map.put("percentage", attempt.getPercentage());
            map.put("dateTaken", attempt.getDateTaken());
            response.add(map);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/student/attempts/{id}")
    public ResponseEntity<?> getAttemptDetails(@PathVariable Long id, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User student = (User) session.getAttribute("user");

        Optional<QuizAttempt> attemptOpt = quizAttemptRepository.findById(id);
        if (attemptOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Attempt report not found."));
        }

        QuizAttempt attempt = attemptOpt.get();
        if (!attempt.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Unauthorized view."));
        }

        Quiz quiz = attempt.getQuiz();
        Map<String, String> submittedAnswers;
        try {
            submittedAnswers = objectMapper.readValue(attempt.getAnswersJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            submittedAnswers = new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", attempt.getId());
        result.put("quizTitle", quiz.getTitle());
        result.put("quizCode", quiz.getQuizCode());
        result.put("score", attempt.getScore());
        result.put("totalQuestions", attempt.getTotalQuestions());
        result.put("percentage", attempt.getPercentage());
        result.put("dateTaken", attempt.getDateTaken());

        List<Map<String, Object>> review = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("questionText", q.getQuestionText());
            rMap.put("optionA", q.getOptionA());
            rMap.put("optionB", q.getOptionB());
            rMap.put("optionC", q.getOptionC());
            rMap.put("optionD", q.getOptionD());
            rMap.put("correctOption", q.getCorrectOption());
            rMap.put("submittedOption", submittedAnswers.get(String.valueOf(q.getId())));
            review.add(rMap);
        }
        result.put("review", review);

        return ResponseEntity.ok(result);
    }

    // --- HELPER METHODS ---

    private Map<String, Object> secureQuiz(Quiz quiz) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", quiz.getId());
        map.put("title", quiz.getTitle());
        map.put("description", quiz.getDescription());
        map.put("quizCode", quiz.getQuizCode());
        map.put("timeLimit", quiz.getTimeLimit());

        List<Map<String, Object>> questionsList = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            Map<String, Object> qMap = new HashMap<>();
            qMap.put("id", q.getId());
            qMap.put("questionText", q.getQuestionText());
            qMap.put("optionA", q.getOptionA());
            qMap.put("optionB", q.getOptionB());
            qMap.put("optionC", q.getOptionC());
            qMap.put("optionD", q.getOptionD());
            questionsList.add(qMap);
        }
        map.put("questions", questionsList);
        return map;
    }

    private List<Map<String, Object>> getAttemptReportList(List<QuizAttempt> attempts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", attempt.getId());
            map.put("studentName", attempt.getStudent().getName());
            map.put("studentEmail", attempt.getStudent().getEmail());
            map.put("quizTitle", attempt.getQuiz().getTitle());
            map.put("quizCode", attempt.getQuiz().getQuizCode());
            map.put("score", attempt.getScore());
            map.put("totalQuestions", attempt.getTotalQuestions());
            map.put("percentage", attempt.getPercentage());
            map.put("dateTaken", attempt.getDateTaken());
            list.add(map);
        }
        return list;
    }
}
