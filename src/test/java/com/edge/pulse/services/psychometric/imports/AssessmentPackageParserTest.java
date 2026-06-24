package com.edge.pulse.services.psychometric.imports;

import com.edge.pulse.data.dto.psychometric.imports.*;
import com.edge.pulse.data.enums.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentPackageParserTest {

    String questions =
        "header,questionEN,questionAR,answerEN1,answerAR1,value1,answerEN2,answerAR2,value2\n" +
        "Q1,Stmt,بيان,A,أ,1,B,ب,2\n";
    String scoringSheet =
        // two row kinds distinguished by a 'rowType' column
        "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi,compositeMethod,compositeBasis,childScales,roundingScale,restricted,questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
        "scale,Agility,,SUM,PARAMETRIC,7.92,3.10,10,50,10,120,,,,,,,,,,,\n" +
        "item,,,,,,,,,,,,,,,,Q1,Agility,FORWARD,BINARY_FORCED_CHOICE,1,\n";
    String answerKey = "header,Q1\nANS,2\n";

    @Test
    void parsesValidPackage() {
        var result = new AssessmentPackageParser().parse(questions, answerKey, scoringSheet);
        assertThat(result.errors()).isEmpty();
        ParsedPackage p = result.pkg();
        assertThat(p.questions()).hasSize(1);
        assertThat(p.questions().get(0).options()).hasSize(2);
        assertThat(p.questions().get(0).options().get(0).displayOrder()).isEqualTo(0);
        assertThat(p.scales()).hasSize(1);
        assertThat(p.scales().get(0).normStrategy()).isEqualTo(NormStrategyType.PARAMETRIC);
        assertThat(p.items()).hasSize(1);
        assertThat(p.items().get(0).itemStrategy()).isEqualTo(ItemStrategyType.BINARY_FORCED_CHOICE);
        assertThat(p.answerKey().get(0).correctValue()).isEqualTo(2);
    }

    @Test
    void reportsItemReferencingUnknownScale() {
        String badSheet = scoringSheet.replace("Agility,FORWARD", "Nonexistent,FORWARD");
        var result = new AssessmentPackageParser().parse(questions, answerKey, badSheet);
        assertThat(result.errors()).anyMatch(e -> e.message().contains("unknown scale"));
    }

    @Test
    void reportsItemReferencingUnknownQuestion() {
        String badSheet = scoringSheet.replace(",Q1,Agility", ",Q99,Agility");
        var result = new AssessmentPackageParser().parse(questions, answerKey, badSheet);
        assertThat(result.errors()).anyMatch(e -> e.message().contains("unknown question"));
    }

    @Test
    void reportsParametricScaleMissingStandardDeviation() {
        // A PARAMETRIC scale that has mean but no sd should produce a validation error
        String sheetMissingSd =
            "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi,compositeMethod,compositeBasis,childScales,roundingScale,restricted,questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
            "scale,Agility,,SUM,PARAMETRIC,7.92,,10,50,10,120,,,,,,,,,,,\n" +
            "item,,,,,,,,,,,,,,,,Q1,Agility,FORWARD,BINARY_FORCED_CHOICE,1,\n";
        var result = new AssessmentPackageParser().parse(questions, answerKey, sheetMissingSd);
        assertThat(result.errors()).anyMatch(e -> e.message().contains("sd"));
    }

    @Test
    void reportsBlankDirectionOnItemRow() {
        // An item row with no direction value should produce a required-direction error
        String sheetBlankDirection =
            "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi,compositeMethod,compositeBasis,childScales,roundingScale,restricted,questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
            "scale,Agility,,SUM,PARAMETRIC,7.92,3.10,10,50,10,120,,,,,,,,,,,\n" +
            "item,,,,,,,,,,,,,,,,Q1,Agility,,BINARY_FORCED_CHOICE,1,\n";
        var result = new AssessmentPackageParser().parse(questions, answerKey, sheetBlankDirection);
        assertThat(result.errors()).anyMatch(e ->
                e.file().equals("scoring_sheet.csv")
                && e.column().equals("direction")
                && e.message().contains("direction is required for item rows"));
    }

    @Test
    void reportsBlankScaleName() {
        // A scale row with a blank name should produce a required-name error
        String sheetBlankName =
            "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi,compositeMethod,compositeBasis,childScales,roundingScale,restricted,questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
            "scale,,,SUM,,,,,,,,,,,,,,,,,,\n";
        var result = new AssessmentPackageParser().parse(questions, answerKey, sheetBlankName);
        assertThat(result.errors()).anyMatch(e ->
                e.file().equals("scoring_sheet.csv")
                && e.column().equals("name")
                && e.message().contains("scale name is required"));
    }

    @Test
    void reportsMissingScoreMethodOnScaleRow() {
        // A scale row with no scoreMethod should produce a required-scoreMethod error
        String sheetNoScoreMethod =
            "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi,compositeMethod,compositeBasis,childScales,roundingScale,restricted,questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
            "scale,Agility,,,,,,,,,,,,,,,,,,,,\n";
        var result = new AssessmentPackageParser().parse(questions, answerKey, sheetNoScoreMethod);
        assertThat(result.errors()).anyMatch(e ->
                e.file().equals("scoring_sheet.csv")
                && e.column().equals("scoreMethod")
                && e.message().contains("scoreMethod is required for scale rows"));
    }

    @Test
    void reportsUnknownAnswerKeyColumn() {
        // The ANS row references a column (QUNKNOWN) that is not a parsed question header
        String badAnswerKey = "header,Q1,QUNKNOWN\nANS,2,1\n";
        var result = new AssessmentPackageParser().parse(questions, badAnswerKey, scoringSheet);
        assertThat(result.errors()).anyMatch(e ->
                e.file().equals("answer_key.csv")
                && e.row().equals("ANS")
                && e.column().equals("QUNKNOWN")
                && e.message().contains("unknown question"));
        // The valid Q1 entry should still be emitted
        assertThat(result.pkg().answerKey()).anyMatch(a -> a.questionHeader().equals("Q1"));
    }

    @Test
    void reportsMalformedOptionValue() {
        // A non-numeric value{k} cell should produce a parse error, not throw
        String badQuestions =
            "header,questionEN,questionAR,answerEN1,answerAR1,value1,answerEN2,answerAR2,value2\n" +
            "Q1,Stmt,بيان,A,أ,NOT_A_NUMBER,B,ب,2\n";
        var result = new AssessmentPackageParser().parse(badQuestions, answerKey, scoringSheet);
        // Should report an error rather than throw
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors()).anyMatch(e -> e.file().equals("questions.csv"));
    }
}
