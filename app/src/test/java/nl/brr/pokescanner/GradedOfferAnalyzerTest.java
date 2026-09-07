package nl.brr.pokescanner;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GradedOfferAnalyzerTest {
    @Test
    public void showsOtherGradersAtSameOrHigherGradeWhenExactMissing() {
        GradedOfferAnalyzer.Analysis a = GradedOfferAnalyzer.analyze(Arrays.asList(
                "seller A | PSA 10 | 120,00 €",
                "seller B | PSA 9 | 80,00 €",
                "seller C | PSA 9 | 75,00 €",
                "seller D | CGC 9.5 | 95,00 €",
                "seller E | TAG 8 | 40,00 €"
        ), "BGS", "9");

        assertEquals(0, a.exactContexts.size());
        assertNull(a.exactMinPrice);
        assertEquals(3, a.alternatives.size());
        assertEquals("PSA", a.alternatives.get(0).grader);
        assertEquals(10.0, a.alternatives.get(0).grade, 0.001);
        assertEquals("CGC", a.alternatives.get(1).grader);
        assertEquals(9.5, a.alternatives.get(1).grade, 0.001);
        assertEquals("PSA", a.alternatives.get(2).grader);
        assertEquals(9.0, a.alternatives.get(2).grade, 0.001);
        assertEquals(2, a.alternatives.get(2).count);
        assertEquals(75.0, a.alternatives.get(2).minPrice, 0.001);
    }

    @Test
    public void normalizesBeckettToBgsAndFindsExact() {
        GradedOfferAnalyzer.Analysis a = GradedOfferAnalyzer.analyze(Arrays.asList(
                "seller | Beckett 9 | 65,00 €",
                "seller | PSA 10 | 120,00 €"
        ), "BGS", "9");

        assertEquals(1, a.exactContexts.size());
        assertEquals(65.0, a.exactMinPrice, 0.001);
        assertEquals(1, a.alternatives.size());
        assertEquals("PSA", a.alternatives.get(0).grader);
    }
}
