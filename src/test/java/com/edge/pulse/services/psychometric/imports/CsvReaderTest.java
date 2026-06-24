package com.edge.pulse.services.psychometric.imports;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CsvReaderTest {
    @Test
    void parsesHeaderAndRows() {
        String csv = "a,b,c\n1,2,3\n4,5,6\n";
        List<Map<String,String>> rows = CsvReader.parse(csv);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("a","1").containsEntry("c","3");
    }
    @Test
    void handlesQuotedFieldsCommasAndEscapedQuotes() {
        String csv = "h1,h2\n\"x, y\",\"she said \"\"hi\"\"\"\n";
        List<Map<String,String>> rows = CsvReader.parse(csv);
        assertThat(rows.get(0)).containsEntry("h1","x, y").containsEntry("h2","she said \"hi\"");
    }
    @Test
    void stripsBomAndHandlesCrlf() {
        String csv = "﻿h1,h2\r\nv1,v2\r\n";
        List<Map<String,String>> rows = CsvReader.parse(csv);
        assertThat(rows.get(0)).containsEntry("h1","v1").containsEntry("h2","v2");
    }
    @Test
    void blankLinesSkipped() {
        String csv = "h\n\nv\n";
        assertThat(CsvReader.parse(csv)).hasSize(1);
    }
}
