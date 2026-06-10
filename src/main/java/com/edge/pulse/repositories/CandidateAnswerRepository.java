package com.edge.pulse.repositories;

import com.edge.pulse.data.models.CandidateAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateAnswerRepository extends JpaRepository<CandidateAnswer, UUID> {

    List<CandidateAnswer> findByQuestionIdOrderByDisplayOrderAsc(UUID questionId);
}
