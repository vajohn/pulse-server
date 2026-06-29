package com.edge.pulse.services.psychometric.assets;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownImageRefsTest {
    @Test
    void extractsAltTextAndUrl() {
        String body = "How many differences?\n![Q1.png](/api/backend/files/abc.png)\n";
        List<MarkdownImageRefs.Ref> refs = MarkdownImageRefs.extract(body);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).alt()).isEqualTo("Q1.png");
        assertThat(refs.get(0).url()).isEqualTo("/api/backend/files/abc.png");
    }

    @Test
    void normalizeFilenameTreatsAraSpaceAndUnderscoreAndCaseEqual() {
        assertThat(MarkdownImageRefs.normalize("Q3_ARA.png")).isEqualTo(MarkdownImageRefs.normalize("Q3 ARA.png"));
        assertThat(MarkdownImageRefs.normalize("Q3_ARA.PNG")).isEqualTo(MarkdownImageRefs.normalize("q3_ara.png"));
    }

    @Test
    void rewriteReplacesUrlPreservingAlt() {
        String body = "x ![Q1.png](/api/backend/files/abc.png) y";
        String out = MarkdownImageRefs.rewrite(body, "/api/backend/files/abc.png", "/api/psychometric/assets/123");
        assertThat(out).contains("![Q1.png](/api/psychometric/assets/123)").doesNotContain("backend/files");
    }

    @Test
    void removeRefStripsTheImageMarkdown() {
        String body = "Caption ![optA.png](optA.png) end";
        MarkdownImageRefs.Ref ref = MarkdownImageRefs.extract(body).get(0);
        String out = MarkdownImageRefs.removeRef(body, ref);
        assertThat(out).isEqualTo("Caption  end").doesNotContain("![");
    }

    @Test
    void removeRefImageOnlyLeavesEmptyAfterTrim() {
        String body = "![optA.png](optA.png)";
        MarkdownImageRefs.Ref ref = MarkdownImageRefs.extract(body).get(0);
        assertThat(MarkdownImageRefs.removeRef(body, ref).trim()).isEqualTo("");
    }
}
