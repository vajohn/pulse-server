package com.edge.pulse.data.dto.psychometric.imports;

import java.util.List;

public record ParsedPackage(
        List<ParsedQuestion> questions,
        List<ScoringSheetScale> scales,
        List<ScoringSheetItem> items,
        List<AnswerKeyEntry> answerKey) {}
