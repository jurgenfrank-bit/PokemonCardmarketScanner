package nl.brr.pokescanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CardOcrParser {
    private static final Pattern GRADER = Pattern.compile("(?i)\\b(PSA|BGS|CGC|TAG|ACE|PCA|SGC)\\b");
    private static final Pattern GRADE_NEAR = Pattern.compile("(?i)\\b(PSA|BGS|CGC|TAG|ACE|PCA|SGC)\\s*(?:GRADE\\s*)?[-:]?\\s*(10(?:\\.0)?|9\\.5|9|8\\.5|8|7\\.5|7|6\\.5|6|5\\.5|5|4|3|2|1)\\b");
    private static final Pattern FRACTION_NUMBER = Pattern.compile("\\b([A-Z]?[A-Z0-9]{0,4}\\s*)?(\\d{1,3})\\s*/\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_NUMBER = Pattern.compile("\\b([A-Z]{2,5})[\\s-]?(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HP = Pattern.compile("(?i)\\s+(?:HP\\s*)?\\d{2,3}\\s*(?:HP)?\\s*$");

    private static final List<String> STOP_WORDS = Arrays.asList(
            "BASIC", "STAGE", "TRAINER", "ENERGY", "POKEMON", "POKÉMON", "GAME", "GEM MT", "MINT",
            "HOLO", "REVERSE", "ILLUSTRATION", "RARE", "CARD", "AUTHENTIC", "CERT", "CERTIFICATE",
            "WEAKNESS", "RESISTANCE", "RETREAT", "ABILITY", "RULE", "SV", "VSTAR", "V-MAX"
    );

    public static Result parse(String raw) {
        String text = raw == null ? "" : raw.replace('\r', '\n');
        String[] split = text.split("\\n+");
        List<String> lines = new ArrayList<>();
        for (String s : split) {
            String t = s.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty()) lines.add(t);
        }

        String grader = "";
        String grade = "";
        Matcher gm = GRADE_NEAR.matcher(text);
        if (gm.find()) {
            grader = gm.group(1).toUpperCase(Locale.ROOT);
            grade = gm.group(2);
        } else {
            Matcher g = GRADER.matcher(text);
            if (g.find()) grader = g.group(1).toUpperCase(Locale.ROOT);
        }

        String number = "";
        Matcher f = FRACTION_NUMBER.matcher(text);
        if (f.find()) {
            number = f.group(2) + "/" + f.group(3);
        } else {
            Matcher sn = SET_NUMBER.matcher(text);
            while (sn.find()) {
                String code = sn.group(1).toUpperCase(Locale.ROOT);
                if (!isLikelyNoiseCode(code)) {
                    number = code + sn.group(2);
                    break;
                }
            }
        }

        String name = guessName(lines, grader);
        String query = (name + " " + number).trim().replaceAll("\\s+", " ");
        return new Result(query, name, number, grader, grade, text);
    }

    private static String guessName(List<String> lines, String grader) {
        for (String line : lines) {
            String candidate = line.replaceFirst("(?i)^\\d{4}\\s+", "").trim();
            String upper = candidate.toUpperCase(Locale.ROOT);
            if (candidate.length() < 3 || candidate.length() > 48) continue;
            if (!candidate.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            if (!grader.isEmpty() && upper.equals(grader)) continue;
            if (upper.matches(".*\\b(PSA|BGS|CGC|TAG|ACE|PCA|SGC)\\b.*")) continue;
            boolean stop = false;
            for (String word : STOP_WORDS) {
                if (upper.equals(word) || upper.startsWith(word + " ")) { stop = true; break; }
            }
            if (stop) continue;
            if (upper.matches(".*\\b20\\d{2}\\b.*") && upper.split(" ").length <= 3) continue;
            if (upper.matches(".*\\d{1,3}\\s*/\\s*\\d{1,3}.*")) continue;
            if (upper.matches(".*\\b[A-Z]{2,5}[ -]?\\d{1,3}\\b.*")) continue;

            String cleaned = HP.matcher(candidate).replaceFirst("");
            cleaned = cleaned.replaceAll("(?i)[–—-]?\\s*(HOLO|REVERSE HOLO)\\s*$", "");
            if (cleaned.length() >= 3) return titleCaseIfAllCaps(cleaned.trim());
        }
        return "";
    }

    private static boolean isLikelyNoiseCode(String code) {
        return Arrays.asList("HP", "PSA", "BGS", "CGC", "TAG", "GEM", "MINT").contains(code);
    }

    private static String titleCaseIfAllCaps(String s) {
        if (!s.equals(s.toUpperCase(Locale.ROOT))) return s;
        StringBuilder b = new StringBuilder();
        for (String p : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }

    public static final class Result {
        public final String query;
        public final String name;
        public final String collectorNumber;
        public final String grader;
        public final String grade;
        public final String rawText;

        Result(String query, String name, String collectorNumber, String grader, String grade, String rawText) {
            this.query = query;
            this.name = name;
            this.collectorNumber = collectorNumber;
            this.grader = grader;
            this.grade = grade;
            this.rawText = rawText;
        }
    }
}
