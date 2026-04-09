package com.nutritioncoach.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NutritionCalcTool.
 *
 * Strategy: verify known-food lookups return correct macro values,
 * unknown foods return a fallback, and edge-case inputs don't throw.
 * All deterministic — no LLM, no network, no Spring context required.
 *
 * MERN analogy: Jest unit tests for a pure-function execute() handler
 * inside a Mastra createTool().  Because the function is deterministic and
 * side-effect-free, tests are fast and reliable.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "A good read-only tool should be testable with assertEquals.
 *   If your tool is hard to test in isolation, it is probably doing
 *   too much — split it."
 */
class NutritionCalcToolTest {

    private final NutritionCalcTool tool = new NutritionCalcTool();

    @Test
    void calculateNutrition_knownFood_salmon_returnsCalories() {
        String result = tool.calculateNutrition("salmon", "100g");

        assertFalse(result.isBlank(), "Salmon result must not be blank");
        // 208 kcal per 100 g × 1.0 scale = 208 kcal
        assertTrue(result.contains("208"), "Salmon 100g should show 208 kcal");
        // Should mention protein
        assertTrue(result.toLowerCase().contains("protein"), "Result should show protein");
    }

    @Test
    void calculateNutrition_scaling_200g_doublesCalories() {
        String result100g = tool.calculateNutrition("chicken breast", "100g");
        String result200g = tool.calculateNutrition("chicken breast", "200g");

        // 165 kcal × 1.0 = 165; × 2.0 = 330
        assertTrue(result100g.contains("165"), "100g should show 165 kcal");
        assertTrue(result200g.contains("330"), "200g should show 330 kcal");
    }

    @Test
    void calculateNutrition_unknownFood_returnsFallback() {
        String result = tool.calculateNutrition("dragonfruit smoothie surprise", "standard serving");

        assertNotNull(result);
        assertFalse(result.isBlank(), "Fallback must not be blank");
        // Fallback mentions the food name
        assertTrue(result.contains("dragonfruit smoothie surprise"),
                "Fallback should echo the food name");
    }

    @Test
    void calculateNutrition_nullFood_doesNotThrow() {
        assertDoesNotThrow(() -> tool.calculateNutrition(null, "100g"),
                "null food must not throw an exception");
    }

    @Test
    void calculateNutrition_blankFood_doesNotThrow() {
        String result = tool.calculateNutrition("", "100g");
        assertNotNull(result);
        assertFalse(result.isBlank(), "Blank food should return a message, not crash");
    }

    @Test
    void calculateNutrition_almonds_containsExpectedFat() {
        // Almonds ≈ 49.9 g fat/100g
        String result = tool.calculateNutrition("almonds", "standard serving");

        assertFalse(result.isBlank());
        assertTrue(result.toLowerCase().contains("fat"), "Result should include fat data");
    }

    @Test
    void calculateNutrition_partialMatch_chickenFindsEntry() {
        // "grilled chicken" should match the "chicken" key via substring
        String result = tool.calculateNutrition("grilled chicken", "100g");

        assertFalse(result.isBlank());
        // Should NOT return the "not available" fallback
        assertFalse(result.contains("not available in local table"),
                "Partial match 'grilled chicken' should resolve to the chicken entry");
    }
}
