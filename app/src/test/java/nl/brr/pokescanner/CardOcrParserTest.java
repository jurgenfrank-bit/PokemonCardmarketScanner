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
}
