package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;

public interface AnswerPersistenceStrategy {
    QuestionType supportedType();
    void persist(AnswerSubmission submission, SubmitAnswerRequest request);
    Object load(AnswerSubmission submission);
}
