package nl.brr.pokescanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradedOfferAnalyzer {
    private static final String GRADE = "(10(?:\\.0)?|9\\.5|9|8\\.5|8|7\\.5|7|6\\.5|6|5\\.5|5|4\\.5|4|3|2|1)";
    private static final Pattern GRADING = Pattern.compile(
            "(?i)\\b(PSA|BGS|BECKETT|CGC|TAG|ACE|PCA|SGC)\\b\\s*(?:GEM\\s*(?:MT|MINT)|MINT|GRADE)?\\s*[-:]?\\s*" + GRADE + "\\b");

    private GradedOfferAnalyzer() {}

    public static Analysis analyze(List<String> contexts, String wantedGrader, String wantedGrade) {
        String wanted = normalizeGrader(wantedGrader);
        double minGrade = parseGrade(wantedGrade);
        List<String> exactContexts = new ArrayList<>();
        List<Double> exactPrices = new ArrayList<>();
        Map<String, AlternativeBuilder> alternatives = new LinkedHashMap<>();

        if (contexts != null) {
            for (String context : contexts) {
                Parsed parsed = parse(context);
                if (parsed == null) continue;

                if (!wanted.isEmpty() && parsed.grader.equals(wanted) && gradesEqual(parsed.grade, minGrade)) {
                    exactContexts.add(context);
                    if (parsed.price != null) exactPrices.add(parsed.price);
                    continue;
                }

                // Only compare other grading companies, at the requested numerical grade or higher.
                if (!wanted.isEmpty() && !parsed.grader.equals(wanted) && minGrade > 0 && parsed.grade >= minGrade) {
                    String key = parsed.grader + "|" + formatGrade(parsed.grade);
                    AlternativeBuilder b = alternatives.get(key);
                    if (b == null) {
                        b = new AlternativeBuilder(parsed.grader, parsed.grade);
                        alternatives.put(key, b);
                    }
                    b.count++;
                    if (parsed.price != null && (b.minPrice == null || parsed.price < b.minPrice)) b.minPrice = parsed.price;
                }
            }
        }

        List<Alternative> result = new ArrayList<>();
        for (AlternativeBuilder b : alternatives.values()) {
            result.add(new Alternative(b.grader, b.grade, b.count, b.minPrice));
        }
        result.sort(Comparator.comparingDouble((Alternative a) -> a.grade).reversed().thenComparing(a -> a.grader));

        Double exactMin = exactPrices.isEmpty() ? null : Collections.min(exactPrices);
        return new Analysis(exactContexts, exactMin, result);
    }

    static Parsed parse(String context) {
        if (context == null) return null;
        Matcher m = GRADING.matcher(context);
        if (!m.find()) return null;
        String grader = normalizeGrader(m.group(1));
        double grade = parseGrade(m.group(2));
        return new Parsed(grader, grade, extractEuroPrice(context));
    }

    static String normalizeGrader(String grader) {
        if (grader == null) return "";
        String g = grader.trim().toUpperCase(Locale.ROOT);
        if (g.equals("BECKETT")) return "BGS";
        return g;
    }

    static double parseGrade(String grade) {
        if (grade == null || grade.trim().isEmpty()) return 0;
        try { return Double.parseDouble(grade.trim().replace(',', '.')); }
        catch (Exception ignored) { return 0; }
    }

    private static boolean gradesEqual(double a, double b) {
        return b > 0 && Math.abs(a - b) < 0.001;
    }

    public static String formatGrade(double grade) {
        if (Math.abs(grade - Math.rint(grade)) < 0.001) return String.valueOf((int)Math.rint(grade));
        return String.format(Locale.US, "%.1f", grade);
    }

    static Double extractEuroPrice(String context) {
        String amount = "([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{2})|[0-9]+(?:[.,][0-9]{2})?)";
        Matcher m = Pattern.compile("(?:€|EUR)\\s*" + amount + "|" + amount + "\\s*€", Pattern.CASE_INSENSITIVE).matcher(context);
        List<Double> candidates = new ArrayList<>();
        while (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                String cleaned = raw.trim();
                if (cleaned.contains(",") && cleaned.contains(".")) cleaned = cleaned.replace(".", "").replace(',', '.');
                else if (cleaned.contains(",")) cleaned = cleaned.replace(',', '.');
                candidates.add(Double.parseDouble(cleaned));
            } catch (Exception ignored) { }
        }
        return candidates.isEmpty() ? null : Collections.min(candidates);
    }

    static final class Parsed {
        final String grader;
        final double grade;
        final Double price;
        Parsed(String grader, double grade, Double price) {
            this.grader = grader;
            this.grade = grade;
            this.price = price;
        }
    }

    private static final class AlternativeBuilder {
        final String grader;
        final double grade;
        int count;
        Double minPrice;
        AlternativeBuilder(String grader, double grade) {
            this.grader = grader;
            this.grade = grade;
        }
    }

    public static final class Alternative {
        public final String grader;
        public final double grade;
        public final int count;
        public final Double minPrice;
        Alternative(String grader, double grade, int count, Double minPrice) {
            this.grader = grader;
            this.grade = grade;
            this.count = count;
            this.minPrice = minPrice;
        }
    }

    public static final class Analysis {
        public final List<String> exactContexts;
        public final Double exactMinPrice;
        public final List<Alternative> alternatives;
        Analysis(List<String> exactContexts, Double exactMinPrice, List<Alternative> alternatives) {
            this.exactContexts = exactContexts;
            this.exactMinPrice = exactMinPrice;
            this.alternatives = alternatives;
        }
    }
}
