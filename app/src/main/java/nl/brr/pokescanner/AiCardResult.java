package nl.brr.pokescanner;

import org.json.JSONObject;

public final class AiCardResult {
    public final String cardName;
    public final String setName;
    public final String setCode;
    public final String collectorNumber;
    public final String language;
    public final boolean graded;
    public final String grader;
    public final String grade;
    public final String gradeLabel;
    public final String year;
    public final String variant;
    public final double confidence;
    public final String searchQuery;
    public final String notes;

    private AiCardResult(String cardName, String setName, String setCode, String collectorNumber,
                         String language, boolean graded, String grader, String grade,
                         String gradeLabel, String year, String variant, double confidence,
                         String searchQuery, String notes) {
        this.cardName = safe(cardName);
        this.setName = safe(setName);
        this.setCode = safe(setCode);
        this.collectorNumber = safe(collectorNumber);
        this.language = safe(language);
        this.graded = graded;
        this.grader = safe(grader);
        this.grade = safe(grade);
        this.gradeLabel = safe(gradeLabel);
        this.year = safe(year);
        this.variant = safe(variant);
        this.confidence = confidence;
        this.searchQuery = safe(searchQuery);
        this.notes = safe(notes);
    }

    public static AiCardResult fromJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        return new AiCardResult(
                o.optString("card_name"),
                o.optString("set_name"),
                o.optString("set_code"),
                o.optString("collector_number"),
                o.optString("language"),
                o.optBoolean("is_graded", false),
                normalizeGrader(o.optString("grader")),
                o.optString("grade"),
                o.optString("grade_label"),
                o.optString("year"),
                o.optString("variant"),
                o.optDouble("confidence", 0.0),
                o.optString("search_query"),
                o.optString("notes")
        );
    }

    private static String normalizeGrader(String value) {
        String g = safe(value).toUpperCase();
        if (g.equals("BECKETT") || g.equals("BECKETT GRADING SERVICES")) return "BGS";
        return g;
    }

    private static String safe(String s) {
        return s == null || "null".equalsIgnoreCase(s) ? "" : s.trim();
    }

    public String summary() {
        StringBuilder b = new StringBuilder();
        if (!cardName.isEmpty()) b.append(cardName);
        if (!setName.isEmpty()) appendPart(b, setName);
        String number = collectorNumber;
        if (!setCode.isEmpty() && !collectorNumber.toUpperCase().startsWith(setCode.toUpperCase())) {
            number = setCode + (collectorNumber.isEmpty() ? "" : " " + collectorNumber);
        }
        if (!number.isEmpty()) appendPart(b, number);
        if (!language.isEmpty()) appendPart(b, language);
        if (graded) {
            String slab = grader;
            if (!grade.isEmpty()) slab += (slab.isEmpty() ? "" : " ") + grade;
            if (!gradeLabel.isEmpty()) slab += (slab.isEmpty() ? "" : " · ") + gradeLabel;
            if (!slab.isEmpty()) appendPart(b, slab);
        } else {
            appendPart(b, "raw");
        }
        return b.toString();
    }

    private static void appendPart(StringBuilder b, String part) {
        if (part == null || part.trim().isEmpty()) return;
        if (b.length() > 0) b.append(" · ");
        b.append(part.trim());
    }
}
