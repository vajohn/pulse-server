package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.dto.SessionDto;
import com.edge.pulse.data.models.ResponseSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionMapper {

    public SessionDto toDto(ResponseSession entity, List<AnswerDto> currentAnswers) {
        return new SessionDto(
                entity.getId(),
                entity.getForm().getId(),
                entity.isAnonymous(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                currentAnswers
        );
    }
}
