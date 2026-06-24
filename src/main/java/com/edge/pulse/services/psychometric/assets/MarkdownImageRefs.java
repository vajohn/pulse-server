package com.edge.pulse.services.psychometric.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses/rewrites markdown image refs {@code ![alt](url)} in question bodies. */
public final class MarkdownImageRefs {
    private static final Pattern IMG = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]*)\\)");

    private MarkdownImageRefs() {
    }

    public record Ref(String alt, String url) {
    }

    public static List<Ref> extract(String body) {
        List<Ref> refs = new ArrayList<>();
        if (body == null) return refs;
        Matcher m = IMG.matcher(body);
        while (m.find()) refs.add(new Ref(m.group(1), m.group(2)));
        return refs;
    }

    /** Filename key: lowercase, drop extension case, treat '_ARA'/' ARA' (any sep) as equal by stripping non-alphanumerics. */
    public static String normalize(String filename) {
        if (filename == null) return "";
        String f = filename.trim().toLowerCase();
        int dot = f.lastIndexOf('.');
        if (dot > 0) f = f.substring(0, dot);
        return f.replaceAll("[^a-z0-9]", ""); // "q3_ara" / "q3 ara" -> "q3ara"
    }

    public static String rewrite(String body, String oldUrl, String newUrl) {
        if (body == null) return null;
        // replace only inside an image ref to avoid accidental matches
        return body.replace("](" + oldUrl + ")", "](" + newUrl + ")");
    }
}
