package com.quizapp.repository;

import com.quizapp.model.Quiz;
import com.quizapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByQuizCode(String quizCode);
    List<Quiz> findByCreator(User creator);
    boolean existsByQuizCode(String quizCode);
}
