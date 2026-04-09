package com.nutritioncoach.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSearchTool.
 *
 * Strategy: verify that the stub returns non-null, non-blank text for
 * known nutrition keywords AND for completely unknown queries (fallback).
 * No Spring context, no LLM calls, no network — pure Java unit test.
 *
 * MERN analogy: a Jest unit test for a createTool() execute function
 * using mocked fetch() responses.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "Test each tool in isolation before wiring it into an agent. A tool
 *   unit test confirms the contract (input → output type) independently
 *   of the LLM that will eventually call it."
 */
class WebSearchToolTest {

    private final WebSearchTool tool = new WebSearchTool();

    @Test
    void searchWeb_knownKeyword_omega3_returnsRelevantContent() {
        String result = tool.searchWeb("omega-3 fatty acids");

        assertNotNull(result, "Result must not be null");
        assertFalse(result.isBlank(), "Result must not be blank");
        // Stub should return a block that mentions the key compound
        assertTrue(result.toLowerCase().contains("omega-3") || result.toLowerCase().contains("epa"),
                "Result for omega-3 query should mention omega-3 or EPA");
    }

    @Test
    void searchWeb_vitaminD_returnsVitaminDContent() {
        String result = tool.searchWeb("vitamin D supplementation");

        assertFalse(result.isBlank());
        assertTrue(result.toLowerCase().contains("vitamin d"),
                "Result for vitamin D query should mention vitamin D");
    }

    @Test
    void searchWeb_unknownTopic_returnsFallbackContent() {
        // A completely unknown query should trigger the fallback, not throw an exception
        String result = tool.searchWeb("exotic mushroom extract NZ-7749");

        assertNotNull(result);
        assertFalse(result.isBlank(), "Fallback result must not be blank");
        // Fallback includes the query string in its output
        assertTrue(result.contains("exotic mushroom extract NZ-7749"),
                "Fallback should echo the query in the output");
    }

    @Test
    void searchWeb_nullLikeEmptyQuery_doesNotThrow() {
        // Should not throw; the tool is read-only and must be safe to call
        assertDoesNotThrow(() -> tool.searchWeb(""),
                "Empty query must not throw an exception");
    }

    @Test
    void searchWeb_proteinKeyword_returnsProteinContent() {
        String result = tool.searchWeb("protein intake for muscle growth");

        assertFalse(result.isBlank());
        assertTrue(result.toLowerCase().contains("protein"),
                "Protein query result should mention protein");
    }
}
