package com.nutritioncoach.agent;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.nutritioncoach.model.LoggerInput;
import com.nutritioncoach.model.LoggerSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * Unit tests for LoggerAgent.summariseHistory()
 * ══════════════════════════════════════════════════════════════════════════
 *
 * No Spring context, no LLM API calls, no DB — fully isolated unit tests.
 * Uses Embabel's FakeOperationContext to intercept the LLM call and return
 * a pre-built LoggerSummary stub.
 *
 * Testing strategy:
 *   1. The conversation messages appear in the outgoing LLM prompt
 *      (context injection is correct — no silent data loss)
 *   2. The @Action returns the value produced by the LLM (wiring correct)
 *   3. Exactly one LLM call is made (no accidental duplicates)
 *   4. Empty message list produces a safe, non-null result
 *
 * MERN/Next.js analogy:
 *   Jest unit tests for a Mastra agent step:
 *     it('includes messages in prompt', async () => {
 *       mockAI.mockResolvedValue(fakeSummary)
 *       await loggerAgent.summarise({ userId: 'u1', messages: ['user: hi'] })
 *       expect(capturedPrompt).toContain('user: hi')
 *     })
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   These tests are the "I/O contract" layer of evals for LoggerAgent:
 *   confirm the agent sends the right data to the LLM and returns the
 *   right structured output before measuring summary quality.
 * ══════════════════════════════════════════════════════════════════════════
 */
class LoggerAgentTest {

    /**
     * Test Matrix (LoggerAgent inputs vs expected behaviour)
     * ┌──────────────────────┬──────────────────────--┐
     * │ Input: messages      │ Input: Empty/null      │
     * ├──────────────────────┼──────────────────────--┤
     * │ Prompt contains msgs │ No NPE, safe result    │
     * │ Returns LLM result   │ Produces LoggerSummary │
     * └──────────────────────┴──────────────────────--┘
     */

    // ── Stub data ──────────────────────────────────────────────────────────

    private static final LoggerSummary FAKE_SUMMARY = new LoggerSummary(
            "User asked about protein sources for vegans.",
            "Build lean muscle on a plant-based diet.",
            "Lactose intolerant, no gluten.",
            List.of(
                    "User is vegan",
                    "Looking for protein alternatives to dairy",
                    "Interested in tempeh and lentils"
            )
    );

    private static final List<String> SAMPLE_MESSAGES = List.of(
            "user: What protein sources are best for vegans?",
            "assistant: Great options include tofu, tempeh, lentils, and chickpeas.",
            "user: I'm lactose intolerant and can't eat gluten either.",
            "assistant: In that case, focus on legumes, hemp seeds, and quinoa."
    );

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void summariseHistory_includesMessagesInPrompt() {
        // Arrange
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SUMMARY);

        var agent = new LoggerAgent();
        var input = new LoggerInput("u1", SAMPLE_MESSAGES);

        // Act
        agent.summariseHistory(input, context.ai());

        // Assert — messages are embedded in the prompt
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(1, invocations.size(), "Expected exactly one LLM call");

        String prompt = invocations.getFirst().getMessages().getFirst().getContent();
        assertTrue(prompt.contains("user: What protein sources are best for vegans?"),
                "Prompt must include the first user message");
        assertTrue(prompt.contains("assistant: Great options include tofu"),
                "Prompt must include the assistant reply");
    }

    @Test
    void summariseHistory_returnsLlmResult() {
        // Arrange
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_SUMMARY);

        var agent = new LoggerAgent();
        var input = new LoggerInput("u1", SAMPLE_MESSAGES);

        // Act
        LoggerSummary result = agent.summariseHistory(input, context.ai());

        // Assert — the stubbed value is returned as-is
        assertEquals(FAKE_SUMMARY, result,
                "summariseHistory must return the LoggerSummary produced by the LLM");
    }

    @Test
    void summariseHistory_exactlyOneLlmCall() {
        // Arrange
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SUMMARY);

        var agent = new LoggerAgent();
        var input = new LoggerInput("u1", SAMPLE_MESSAGES);

        // Act
        agent.summariseHistory(input, context.ai());

        // Assert — no duplicate LLM calls
        assertEquals(1, promptRunner.getLlmInvocations().size(),
                "LoggerAgent must make exactly one LLM call per invocation");
    }

    @Test
    void summariseHistory_summaryFieldsMatchStub() {
        // Verify individual fields are correctly mapped from LLM output
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_SUMMARY);

        var agent = new LoggerAgent();
        LoggerSummary result = agent.summariseHistory(
                new LoggerInput("u1", SAMPLE_MESSAGES), context.ai());

        assertEquals("User asked about protein sources for vegans.",
                result.compressedSummary(), "compressedSummary must match stub");
        assertEquals("Build lean muscle on a plant-based diet.",
                result.extractedGoals(), "extractedGoals must match stub");
        assertEquals("Lactose intolerant, no gluten.",
                result.extractedRestrictions(), "extractedRestrictions must match stub");
        assertEquals(3, result.keyFacts().size(),
                "keyFacts must contain all 3 stub facts");
        assertTrue(result.keyFacts().contains("User is vegan"),
                "keyFacts must include 'User is vegan'");
    }

    @Test
    void summariseHistory_emptyMessages_promptStillBuilds() {
        // Even with an empty message list the agent should not throw —
        // it just builds a prompt with an empty conversation block.
        var context = FakeOperationContext.create();
        context.expectResponse(new LoggerSummary("", "", "", List.of()));

        var agent = new LoggerAgent();
        var input = new LoggerInput("u1", List.of());

        // Should not throw
        assertDoesNotThrow(() -> agent.summariseHistory(input, context.ai()),
                "summariseHistory must not throw for an empty message list");
    }
}
