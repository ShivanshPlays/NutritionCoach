package com.nutritioncoach.agent;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.nutritioncoach.model.ResearchBrief;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit test for ResearchAgent
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   This is the equivalent of a Jest unit test for a Mastra agent action:
 *
 *     // Jest (Node.js Mastra):
 *     jest.mock('../lib/ai')
 *     it('includes topic in prompt', async () => {
 *       mockAI.mockResolvedValue(fakeBrief)
 *       await researchAgent.gatherFacts('omega-3')
 *       expect(capturedPrompt).toContain('omega-3')
 *     })
 *
 *     // Embabel (Java):
 *     var context = FakeOperationContext.create()
 *     context.expectResponse(fakeBrief)            // ← stub the LLM response
 *     agent.gatherFacts(userInput, context.ai())    // ← call action directly
 *     verify prompt via promptRunner.getLlmInvocations()
 *
 * FakeOperationContext:
 *   Embabel's built-in test double that intercepts LLM calls.
 *   No Spring context, no real HTTP, no Gemini API key needed.
 *   MERN analogy: jest.mock('@ai-sdk/google') — replace the real AI client
 *   with a stub that returns canned data.
 *
 * FakePromptRunner:
 *   Records every LLM invocation (prompt text, model, temperature, tool groups).
 *   You can assert that the prompt contains the right data and hyperparameters.
 *   MERN analogy: a spy on generateObject() that captures what was called.
 *
 * Testing strategy:
 *   We test THREE things here:
 *     1. The topic string appears in the outgoing LLM prompt
 *        (sanity check: the agent didn't silently drop the input)
 *     2. The action method returns the value from the LLM (wiring is correct)
 *     3. Exactly one LLM call was made (no accidental duplicates)
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   Unit tests like this are the "I/O contract" layer of evals:
 *   confirm the agent calls the LLM with the expected inputs before measuring
 *   output quality.  Fast, deterministic, no real API quota consumed.
 *
 * Book ref: Chapter 28 — Writing LLM Evals
 *   Prompt content checks ("prompt contains the topic") are the simplest
 *   category of eval — verifying that context is properly injected.
 */
class ResearchAgentTest {

    // Pre-built stub that the fake LLM will "return"
    private static final ResearchBrief FAKE_BRIEF = new ResearchBrief(
            "omega-3 fatty acids",
            List.of(
                    "Essential polyunsaturated fatty acids EPA and DHA",
                    "Reduce triglycerides and inflammation markers"
            ),
            List.of(
                    "May increase bleeding time at doses > 3 g/day",
                    "Fish-derived supplements may contain mercury traces"
            ),
            List.of(
                    "What is the optimal EPA:DHA ratio for cardiovascular benefit?",
                    "How does plant-based ALA conversion to EPA compare to fish oil?"
            )
    );

    @Test
    void gatherFacts_includesTopicInPrompt() {
        // Arrange ─────────────────────────────────────────────────────────
        // FakeOperationContext intercepts the LLM call and returns FAKE_BRIEF
        // instead of making a real HTTP call to Gemini.
        // MERN analogy: jest.spyOn(ai, 'generateObject').mockResolvedValue(fakeBrief)
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_BRIEF);

        var agent = new ResearchAgent();
        var topic = "omega-3 fatty acids";

        // Act ─────────────────────────────────────────────────────────────
        // Call the @Action method directly, passing context.ai() as the Ai param.
        // In production, Embabel injects the real Ai; in tests, we inject the fake.
        var result = agent.gatherFacts(
                new UserInput(topic, Instant.now()),
                context.ai()   // ← provide the fake Ai, not the real one
        );

        // Assert — topic is in prompt ─────────────────────────────────────
        // Retrieve the actual prompt string that was passed to the LLM.
        // This verifies the agent correctly embeds the user's topic into
        // the outgoing prompt (i.e., no silent data loss).
        var llmInvocations = promptRunner.getLlmInvocations();
        assertEquals(1, llmInvocations.size(), "Expected exactly one LLM call");

        String prompt = llmInvocations.getFirst().getMessages().getFirst().getContent();
        assertTrue(
                prompt.contains(topic),
                "Expected prompt to contain the topic '" + topic + "' but got:\n" + prompt
        );

        // Assert — the stubbed brief is returned as-is ────────────────────
        assertEquals(FAKE_BRIEF, result,
                "Expected the agent to return the value produced by the LLM");
    }
}
