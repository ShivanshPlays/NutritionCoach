package com.nutritioncoach.guardrail;

import com.nutritioncoach.model.CoachAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OutputModerator — unsafe advice detection.
 *
 * No Spring context required; plain Java tests.
 *
 * MERN/Next.js analogy:
 *   Equivalent of Jest tests for a response-interceptor utility:
 *     it('blocks advice claiming to cure cancer', () => {
 *       expect(() => moderator.check(unsafeAdvice)).toThrow()
 *     })
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Output moderation tests should cover every category of unsafe content
 *   that could appear in nutritional advice.
 */
class OutputModeratorTest {

    private OutputModerator moderator;

    @BeforeEach
    void setUp() {
        moderator = new OutputModerator();
    }

    // ── Safe advice should pass through ───────────────────────────────────

    @Test
    void safeAdvicePassesThrough() {
        CoachAdvice safe = new CoachAdvice(
                "Eat more leafy greens and lean protein to support your fitness goals.",
                List.of("Add 150g salmon three times per week",
                        "Swap refined carbs for sweet potato"),
                "Consume at least 5 servings of vegetables daily",
                "This is general nutrition advice, not medical advice. Consult a registered dietitian.");
        assertDoesNotThrow(() -> moderator.check(safe));
    }

    @Test
    void nullAdviceSkipped() {
        assertDoesNotThrow(() -> moderator.check(null));
    }

    @Test
    void disclaimerWithMedicalWordsAllowed() {
        // The word "medical" in a proper disclaimer must not trigger moderation
        CoachAdvice withDisclaimer = new CoachAdvice(
                "High-protein diets support muscle recovery.",
                List.of("Consume 1.6g protein per kg body weight"),
                "Hit your daily protein target",
                "This is not medical advice; consult your doctor before making dietary changes.");
        assertDoesNotThrow(() -> moderator.check(withDisclaimer));
    }

    // ── Unsafe patterns should throw UnsafeOutputException ────────────────

    @Test
    void medicalClaimCureCancerBlocked() {
        CoachAdvice unsafe = new CoachAdvice(
                "This diet will cure your cancer with sustained use.",
                List.of("Eat only raw foods"),
                "Eliminate all processed foods",
                "No disclaimer.");
        assertThrows(UnsafeOutputException.class, () -> moderator.check(unsafe));
    }

    @Test
    void medicalClaimTreatDiabetesBlocked() {
        CoachAdvice unsafe = new CoachAdvice(
                "A ketogenic diet can treat your diabetes effectively.",
                List.of("Remove all carbohydrates"),
                "Zero carbs per day",
                "Not medical advice.");
        assertThrows(UnsafeOutputException.class, () -> moderator.check(unsafe));
    }

    @Test
    void stopMedicationBlocked() {
        CoachAdvice unsafe = new CoachAdvice(
                "Following this plan you can stop taking your medication.",
                List.of("Take 2000mg vitamin C daily"),
                "Reduce all supplements",
                "No disclaimer.");
        assertThrows(UnsafeOutputException.class, () -> moderator.check(unsafe));
    }

    @Test
    void actionItemContainingUnsafeContentBlocked() {
        // Unsafe content in an action item (not just summary) should be caught
        CoachAdvice unsafe = new CoachAdvice(
                "Focus on whole foods.",
                List.of("Stop taking your prescription drugs", "Eat more vegetables"),
                "Reduce processed food",
                "Not medical advice.");
        assertThrows(UnsafeOutputException.class, () -> moderator.check(unsafe));
    }
}
