package com.quizapp.repository;

import com.quizapp.model.Quiz;
import com.quizapp.model.QuizAttempt;
import com.quizapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByStudentOrderByDateTakenDesc(User student);
    List<QuizAttempt> findByQuizOrderByDateTakenDesc(Quiz quiz);
    List<QuizAttempt> findByQuizCreatorOrderByDateTakenDesc(User creator);
}
