package nl.brr.pokescanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CardOcrParser {
    private static final String GRADE_VALUE = "(10(?:\\.0)?|9\\.5|9|8\\.5|8|7\\.5|7|6\\.5|6|5\\.5|5|4\\.5|4|3|2|1)";

    private static final Pattern GRADER = Pattern.compile("(?i)\\b(PSA|BGS|BECKETT|CGC|TAG|ACE|PCA|SGC)\\b");
    private static final Pattern GRADE_NEAR = Pattern.compile(
            "(?i)\\b(PSA|BGS|BECKETT|CGC|TAG|ACE|PCA|SGC)\\b\\s*(?:GEM\\s*(?:MT|MINT)\\s*)?(?:GRADE\\s*)?[-:]?\\s*" + GRADE_VALUE + "\\b");
    private static final Pattern GRADE_ONLY = Pattern.compile(
            "(?i)\\b(?:GEM\\s*(?:MT|MINT)|MINT|GRADE)\\s*[-:]?\\s*" + GRADE_VALUE + "\\b");
    private static final Pattern GRADE_REVERSED = Pattern.compile(
            "(?i)\\b" + GRADE_VALUE + "\\s*(?:GEM\\s*)?(?:MINT|MT)\\b");

    private static final Pattern BECKETT_SUBGRADE = Pattern.compile(
            "(?i)\\b(CENTERING|CORNERS|EDGES|SURFACE)\\s*[:\\-]?\\s*" + GRADE_VALUE + "\\b");

    // Typical Beckett label: "#84 AUDINO EX HOLO R".
    // OCR sometimes merges the last words into "HOLOR".
    private static final Pattern SLAB_CARD_LINE = Pattern.compile(
            "(?i)^#?\\s*(\\d{1,3})\\s+(.+?)\\s*$");

    private static final Pattern FRACTION_NUMBER = Pattern.compile("\\b(\\d{1,3})\\s*/\\s*(\\d{1,3})\\b");
    private static final Pattern SET_NUMBER = Pattern.compile("\\b([A-Z]{2,6})[\\s-]?(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HP = Pattern.compile("(?i)\\s+(?:HP\\s*)?\\d{2,3}\\s*(?:HP)?\\s*$");
    private static final Pattern YEAR_PREFIX = Pattern.compile("^(?:19|20)\\d{2}\\s+");

    private static final List<String> NOISE_EXACT = Arrays.asList(
            "BASIC", "STAGE", "TRAINER", "ENERGY", "POKEMON", "POKÉMON", "GAME",
            "GEM MT", "GEM MINT", "MINT", "GEM", "AUTHENTIC", "CERT", "CERTIFICATE",
            "HOLO", "HOLOR", "REVERSE", "ILLUSTRATION RARE", "RARE", "CARD", "WEAKNESS",
            "RESISTANCE", "RETREAT", "ABILITY", "RULE", "SV", "VSTAR", "V-MAX"
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
            grader = normalizeGrader(gm.group(1));
            grade = gm.group(2);
        } else {
            Matcher g = GRADER.matcher(text);
            if (g.find()) grader = normalizeGrader(g.group(1));

            Matcher go = GRADE_ONLY.matcher(text);
            if (go.find()) grade = go.group(1);
            if (grade.isEmpty()) {
                Matcher gr = GRADE_REVERSED.matcher(text);
                if (gr.find()) grade = gr.group(1);
            }
        }

        // Beckett slabs can be identified even when OCR misses the logo/name.
        // Their four subgrade headings are highly characteristic.
        String upperText = text.toUpperCase(Locale.ROOT);
        boolean beckettLabel = looksLikeBeckettLabel(upperText);
        if (grader.isEmpty() && beckettLabel) {
            grader = "BGS";
        }

        // If the large final grade / MINT pair was missed by OCR, infer it only
        // when at least three Beckett subgrades were read and every read value agrees.
        // This makes the fallback conservative: 9/9/9/9 => 9, but mixed subgrades
        // are never converted into an assumed final grade.
        if (grade.isEmpty() && "BGS".equals(grader) && beckettLabel) {
            grade = inferBeckettGradeFromUniformSubgrades(text);
        }

        String name = "";
        String collectorNumber = "";
        String searchNumber = "";

        // First priority: explicit slab label row such as #84 AUDINO EX HOLO R.
        for (String line : lines) {
            Matcher sm = SLAB_CARD_LINE.matcher(line);
            if (sm.matches()) {
                String n = cleanSlabCardName(sm.group(2));
                if (isPlausibleCardName(n, grader)) {
                    collectorNumber = sm.group(1);
                    searchNumber = sm.group(1);
                    name = titleCaseIfAllCaps(n);
                    break;
                }
            }
        }

        // Raw cards / slabs where the label number row was not OCR'd.
        if (collectorNumber.isEmpty()) {
            Matcher f = FRACTION_NUMBER.matcher(text);
            if (f.find()) {
                collectorNumber = f.group(1) + "/" + f.group(2);
                searchNumber = f.group(1);
            } else {
                Matcher sn = SET_NUMBER.matcher(text);
                while (sn.find()) {
                    String code = sn.group(1).toUpperCase(Locale.ROOT);
                    if (!isLikelyNoiseCode(code)) {
                        collectorNumber = code + sn.group(2);
                        searchNumber = collectorNumber;
                        break;
                    }
                }
            }
        }

        if (name.isEmpty()) name = guessName(lines, grader);

        String query = (name + " " + searchNumber).trim().replaceAll("\\s+", " ");
        return new Result(query, name, collectorNumber, grader, grade, text);
    }

    private static boolean looksLikeBeckettLabel(String upperText) {
        if (upperText.contains("BECKETT")) return true;
        int hits = 0;
        if (upperText.contains("CENTERING")) hits++;
        if (upperText.contains("CORNERS")) hits++;
        if (upperText.contains("EDGES")) hits++;
        if (upperText.contains("SURFACE")) hits++;
        return hits >= 3;
    }

    private static String inferBeckettGradeFromUniformSubgrades(String text) {
        Matcher m = BECKETT_SUBGRADE.matcher(text);
        List<String> grades = new ArrayList<>();
        while (m.find()) {
            grades.add(m.group(2));
        }
        if (grades.size() < 3) return "";
        String first = grades.get(0);
        for (String g : grades) {
            if (!first.equals(g)) return "";
        }
        return first;
    }

    private static String normalizeGrader(String raw) {
        if (raw == null) return "";
        String g = raw.trim().toUpperCase(Locale.ROOT);
        if (g.equals("BECKETT")) return "BGS";
        return g;
    }

    private static String cleanSlabCardName(String s) {
        String x = s == null ? "" : s.trim();

        // Normal spellings plus frequent OCR merges/errors on slab labels.
        // Examples: HOLO R, HOLOR, HOL0R, H0L0 R.
        x = x.replaceAll("(?i)\\s+(?:H[O0]L[O0]\\s*R?|HOLOR|REVERSE(?:\\s+H[O0]L[O0])?|NON[- ]?H[O0]L[O0]|RARE|R)\\s*$", "");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private static boolean isPlausibleCardName(String candidate, String grader) {
        if (candidate == null || candidate.length() < 2 || candidate.length() > 48) return false;
        if (!candidate.matches(".*[A-Za-zÀ-ÿ].*")) return false;
        return !isMetadataLine(candidate.toUpperCase(Locale.ROOT), grader);
    }

    private static String guessName(List<String> lines, String grader) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < lines.size(); i++) {
            String original = lines.get(i);
            String candidate = original.trim();
            String upperOriginal = candidate.toUpperCase(Locale.ROOT);

            if (isMetadataLine(upperOriginal, grader)) continue;
            if (FRACTION_NUMBER.matcher(candidate).find()) continue;
            if (looksLikeSetCodeNumber(candidate)) continue;

            candidate = YEAR_PREFIX.matcher(candidate).replaceFirst("").trim();
            candidate = HP.matcher(candidate).replaceFirst("").trim();
            candidate = cleanSlabCardName(candidate);
            candidate = candidate.replaceAll("(?i)\\b(?:GEM\\s*(?:MT|MINT)|MINT|GRADE)\\s*" + GRADE_VALUE + "\\b", "").trim();

            if (candidate.length() < 2 || candidate.length() > 48) continue;
            if (!candidate.matches(".*[A-Za-zÀ-ÿ].*")) continue;
            if (candidate.matches("^[0-9 .#/-]+$")) continue;

            String upper = candidate.toUpperCase(Locale.ROOT);
            if (isMetadataLine(upper, grader)) continue;

            int words = candidate.split("\\s+").length;
            int score = 100 - (i * 4);
            if (words >= 1 && words <= 4) score += 16;
            if (!candidate.matches(".*\\d.*")) score += 10;
            if (upper.matches(".*\\b(EX|GX|V|VMAX|VSTAR|LV\\.?X|BREAK)\\b.*")) score += 18;
            if (upper.contains("ATTACK") || upper.contains("DAMAGE")) score -= 30;

            if (score > bestScore) {
                bestScore = score;
                best = titleCaseIfAllCaps(candidate);
            }
        }
        return best;
    }

    private static boolean isMetadataLine(String upper, String grader) {
        String u = upper.trim();
        if (u.isEmpty()) return true;

        for (String word : NOISE_EXACT) {
            if (u.equals(word) || u.startsWith(word + " ")) return true;
        }

        if (!grader.isEmpty() && (u.equals(grader) || u.startsWith(grader + " "))) return true;
        if (u.matches(".*\\b(PSA|BGS|BECKETT|CGC|TAG|ACE|PCA|SGC)\\b.*")) return true;
        if (u.matches(".*\\bGEM\\s+(?:MT|MINT)\\b.*")) return true;
        if (u.matches(".*\\bMINT\\s*" + GRADE_VALUE + "\\b.*")) return true;
        if (u.matches(".*\\b" + GRADE_VALUE + "\\s*MINT\\b.*")) return true;
        if (u.matches(".*\\bGRADE\\s*" + GRADE_VALUE + "\\b.*")) return true;

        // Any year-led slab line is metadata/set information, not the card name.
        if (u.matches("^(19|20)\\d{2}\\b.*")) return true;

        // Beckett subgrade rows must never become card names.
        if (u.contains("CENTERING") || u.contains("CORNERS") || u.contains("EDGES") || u.contains("SURFACE")) return true;

        if (u.contains("CERT #") || u.contains("CERTIFICATION") || u.contains("AUTHENTIC")) return true;
        if (u.contains(" ENGLISH") || u.equals("ENGLISH") || u.contains(" JAPANESE") || u.equals("JAPANESE")) return true;
        if (u.startsWith("POKEMON ") || u.startsWith("POKÉMON ")) return true;
        return false;
    }

    private static boolean looksLikeSetCodeNumber(String text) {
        Matcher sn = SET_NUMBER.matcher(text);
        if (!sn.find()) return false;
        String code = sn.group(1).toUpperCase(Locale.ROOT);
        return !isLikelyNoiseCode(code) && text.trim().length() <= 12;
    }

    private static boolean isLikelyNoiseCode(String code) {
        return Arrays.asList("HP", "PSA", "BGS", "BECKETT", "CGC", "TAG", "ACE", "PCA", "SGC", "GEM", "MINT", "GRADE").contains(code);
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
