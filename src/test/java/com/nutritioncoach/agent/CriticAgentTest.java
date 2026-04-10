package com.nutritioncoach.agent;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.CriticScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit test for CriticAgent — LLM-as-judge evaluation of CoachAdvice
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Uses the same FakeOperationContext pattern as CoachAgentTest.
 * CriticAgent makes exactly 1 LLM call per critique invocation.
 *
 * MERN/Next.js analogy:
 *   Jest test for a Mastra LLM-judge agent:
 *     it('sends advice text to the LLM', async () => {
 *       const score = await criticAgent.critique(fakeAdvice)
 *       expect(capturedPrompt).toContain(fakeAdvice.summary)
 *     })
 *
 * Book ref: Chapter 28 — Writing LLM Evals
 *   "Test the I/O contract of the judge itself: verify the rubric appears
 *    in the outgoing prompt and that the score record is correctly returned."
 * ═══════════════════════════════════════════════════════════════════════════
 */
class CriticAgentTest {

    /** Advice under evaluation — safe, plausible nutrition content. */
    private static final CoachAdvice SAMPLE_ADVICE = new CoachAdvice(
            "Creatine monohydrate supports strength gains with strong research evidence.",
            List.of("Take 3-5g daily", "Stay hydrated"),
            "Complete creatine loading this week",
            "This is informational only. Consult a registered dietitian."
    );

    /** Stubbed CriticScore the fake LLM will return. */
    private static final CriticScore FAKE_SCORE = new CriticScore(85, true,
            "Advice is specific, evidence-grounded, and safe.");

    // ── Prompt content checks ──────────────────────────────────────────────

    @Test
    void critique_includesAdviceSummaryInPrompt() {
        // Arrange
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SCORE);

        var agent = new CriticAgent();

        // Act
        agent.critique(SAMPLE_ADVICE, context.ai());

        // Assert — summary must appear in the prompt so the judge can evaluate it
        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains(SAMPLE_ADVICE.summary()),
                "Prompt must contain the advice summary for the LLM judge to see it");
    }

    @Test
    void critique_includesAdvisoryKeywordsInPrompt() {
        // The prompt rubric words (SCORING RUBRIC, SAFETY FLAG) must be present
        // to ensure the prompt template is assembled correctly.
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SCORE);

        new CriticAgent().critique(SAMPLE_ADVICE, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains("SCORING RUBRIC"),
                "Prompt must include the scoring rubric section");
        assertTrue(prompt.contains("SAFETY FLAG"),
                "Prompt must include the safety flag section");
    }

    @Test
    void critique_includesActionItemsInPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SCORE);

        new CriticAgent().critique(SAMPLE_ADVICE, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        // Action items are joined with "; " — verify at least the first item appears
        assertTrue(prompt.contains("Take 3-5g daily"),
                "Prompt must include the advice action items");
    }

    // ── LLM call count ─────────────────────────────────────────────────────

    @Test
    void critique_makesExactlyOneLlmCall() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_SCORE);

        new CriticAgent().critique(SAMPLE_ADVICE, context.ai());

        assertEquals(1, promptRunner.getLlmInvocations().size(),
                "CriticAgent must make exactly 1 LLM call per critique invocation");
    }

    // ── Return value ───────────────────────────────────────────────────────

    @Test
    void critique_returnsStubbedCriticScore() {
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_SCORE);

        CriticScore result = new CriticAgent().critique(SAMPLE_ADVICE, context.ai());

        assertEquals(FAKE_SCORE.score(), result.score(),
                "Returned score must match the stubbed LLM response");
        assertEquals(FAKE_SCORE.safe(), result.safe(),
                "Returned safe flag must match the stubbed LLM response");
        assertEquals(FAKE_SCORE.feedback(), result.feedback(),
                "Returned feedback must match the stubbed LLM response");
    }

    @Test
    void critique_nullActionItemsHandledGracefully() {
        // CoachAdvice with null actionItems shouldn't cause NPE in critique
        var advice = new CoachAdvice(
                "Eat more vegetables.",
                null,   // null action items
                "5 servings of veg daily",
                "Not medical advice.");
        var context = FakeOperationContext.create();
        context.expectResponse(new CriticScore(70, true, "Acceptable advice."));

        assertDoesNotThrow(() -> new CriticAgent().critique(advice, context.ai()),
                "Null actionItems must not cause a NullPointerException in critique()");
    }
}
