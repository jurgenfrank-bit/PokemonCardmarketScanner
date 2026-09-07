package nl.brr.pokescanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardOcrParserTest {

    @Test
    public void psaSlabDoesNotUseGemMintAsCardName() {
        String ocr = "PSA\nGEM MT 10\n2023 POKEMON SVI EN\nCHARIZARD EX\n223/197\nCERT 12345678";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("PSA", r.grader);
        assertEquals("10", r.grade);
        assertEquals("Charizard Ex", r.name);
        assertEquals("223/197", r.collectorNumber);
        assertEquals("Charizard Ex 223", r.query);
    }

    @Test
    public void slabWithoutReadableLogoKeepsGradeButRejectsMint() {
        String ocr = "GEM MINT 10\n2021 POKEMON\nPIKACHU VMAX\n188/185";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("", r.grader);
        assertEquals("10", r.grade);
        assertEquals("Pikachu Vmax", r.name);
        assertFalse(r.query.toLowerCase().contains("mint"));
        assertEquals("Pikachu Vmax 188", r.query);
    }

    @Test
    public void rawCardUsesPokemonNameAndVisibleNumber() {
        String ocr = "Pikachu 60 HP\nGnaw\n10\nPika Bolt\n30\n088/193";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("Pikachu", r.name);
        assertEquals("088/193", r.collectorNumber);
        assertEquals("Pikachu 088", r.query);
        assertTrue(r.grader.isEmpty());
    }

    @Test
    public void slowpokeMepPromoRejectsBasigAndKeepsSetCode() {
        String ocr = "BASIG\nSlowpoke\nHP 80\nNO. 0079 Dopey Pokemon\nAbility Dopey Face\nSuper Psy Bolt 50\nJ MEP EN 086";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("Slowpoke", r.name);
        assertEquals("MEP 086", r.collectorNumber);
        assertEquals("Slowpoke MEP 086", r.query);
        assertTrue(r.grader.isEmpty());
        assertTrue(r.grade.isEmpty());
    }

    @Test
    public void rawPromoDoesNotUseAttackDamageAsSlabCardNumber() {
        String ocr = "BASIC\nSlowpoke\nHP 80\n50 Super Psy Bolt\nMEP EN 086";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("Slowpoke", r.name);
        assertEquals("MEP 086", r.collectorNumber);
        assertEquals("Slowpoke MEP 086", r.query);
    }

    @Test
    public void beckettAudinoLabelIsRecognizedFromSubgradesAndLabelRow() {
        String ocr = "2016 XY FATES COLLIDE\n#84 AUDINO EX HOLO R\n9\nMINT\n0019902509\nCENTERING 9 CORNERS 9\nEDGES 9 SURFACE 9";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("BGS", r.grader);
        assertEquals("9", r.grade);
        assertEquals("Audino Ex", r.name);
        assertEquals("84", r.collectorNumber);
        assertEquals("Audino Ex 84", r.query);
    }

    @Test
    public void beckettWordAlsoNormalizesToBgs() {
        String ocr = "BECKETT\n2016 XY FATES COLLIDE\n#84 AUDINO EX HOLO R\nMINT 9";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("BGS", r.grader);
        assertEquals("9", r.grade);
        assertEquals("Audino Ex", r.name);
        assertEquals("Audino Ex 84", r.query);
    }

    @Test
    public void mergedHolorIsRemovedFromBeckettCardName() {
        String ocr = "2016 XY FATES COLLIDE\n#84 AUDINO EX HOLOR\nCENTERING 9 CORNERS 9\nEDGES 9 SURFACE 9\n0019902509";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("BGS", r.grader);
        assertEquals("9", r.grade);
        assertEquals("Audino Ex", r.name);
        assertEquals("84", r.collectorNumber);
        assertEquals("Audino Ex 84", r.query);
    }

    @Test
    public void uniformBeckettSubgradesProvideConservativeGradeFallback() {
        String ocr = "2016 XY FATES COLLIDE\n#84 AUDINO EX HOLOR\nCENTERING 9\nCORNERS 9\nEDGES 9\nSURFACE 9";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("BGS", r.grader);
        assertEquals("9", r.grade);
    }

    @Test
    public void mixedBeckettSubgradesDoNotInventFinalGrade() {
        String ocr = "2016 XY FATES COLLIDE\n#84 AUDINO EX HOLOR\nCENTERING 9\nCORNERS 9\nEDGES 8.5\nSURFACE 9";
        CardOcrParser.Result r = CardOcrParser.parse(ocr);
        assertEquals("BGS", r.grader);
        assertEquals("", r.grade);
    }
}
