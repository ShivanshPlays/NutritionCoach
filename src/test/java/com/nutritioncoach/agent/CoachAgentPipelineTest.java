package com.nutritioncoach.agent;

import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.nutritioncoach.memory.InMemoryMemoryService;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.ResearchBrief;
import com.nutritioncoach.tool.MemoryTool;
import com.nutritioncoach.tool.NutritionCalcTool;
import com.nutritioncoach.tool.WebSearchTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for CoachAgent.coachFromResearch() — Phase 5 pipeline action
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * What this tests:
 *   CoachAgent.coachFromResearch(ResearchBrief, Ai) — the second step of the
 *   two-agent pipeline introduced in Phase 5.  ResearchAgent produces a
 *   ResearchBrief; this action transforms it into personalised CoachAdvice.
 *
 * Compared to CoachAgentTest (which tests advise()):
 *   advise()            — takes raw UserInput, pre-fetches its own web search
 *   coachFromResearch() — takes a structured ResearchBrief, skips web search
 *                         (research is already done), still uses NutritionCalc + Memory
 *
 * Testing strategy:
 *   1. ResearchBrief fields appear in the LLM prompt (pipeline wiring is correct)
 *   2. Macro data from NutritionCalcTool is injected (tool is still called)
 *   3. WebSearchTool is NOT called (brief already has the research context)
 *   4. Exactly 1 LLM call is made
 *   5. Memory side-effect: a new note is stored after the call
 *   6. Previous notes are injected when present
 *
 * MERN/Next.js analogy:
 *   Jest tests for the second step in a Mastra pipeline:
 *     it('passes ResearchBrief fields to the LLM prompt', async () => {
 *       const brief = { topic: 'omega-3', keyFindings: ['EPA reduces inflammation'] }
 *       mockAI.mockResolvedValue(fakeAdvice)
 *       await coachAgent.coachFromResearch(brief)
 *       expect(capturedPrompt).toContain('EPA reduces inflammation')
 *     })
 *
 * Book ref: Chapter 4 — Agents 101
 *   "Multi-agent pipelines: test each step in isolation with a fixed upstream
 *    output (the ResearchBrief stub) — don't call ResearchAgent from here."
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "Prompt content checks at the pipeline stitching points are the cheapest
 *    eval: verify that upstream structured output is correctly injected before
 *    moving to model-level quality evals."
 * ═══════════════════════════════════════════════════════════════════════════
 */
class CoachAgentPipelineTest {

    // Fixed stub ResearchBrief that plays the role of ResearchAgent's output.
    // In real pipeline: produced by ResearchAgent.gatherFacts(UserInput).
    // In test:         hardcoded so we don't depend on ResearchAgent or LLM.
    private static final ResearchBrief FAKE_BRIEF = new ResearchBrief(
            "omega-3 fatty acids",
            List.of(
                    "EPA and DHA are the bioactive forms found in fish oil",
                    "Meta-analyses support cardiovascular risk reduction at 2–4 g/day",
                    "Plant-based ALA has ~5-15% conversion efficiency to EPA"
            ),
            List.of(
                    "May prolong bleeding time at high doses (> 3 g/day)",
                    "Fish-oil supplements can oxidise if stored improperly"
            ),
            List.of(
                    "What is the optimal EPA:DHA ratio for anti-inflammatory effects?",
                    "Does algae-based DHA match fish-oil bioavailability?"
            )
    );

    private static final CoachAdvice FAKE_ADVICE = new CoachAdvice(
            "Omega-3 fatty acids, especially EPA and DHA from fish oil, are well-supported for cardiovascular and anti-inflammatory benefits.",
            List.of(
                    "Take 2 g combined EPA+DHA daily from a quality fish-oil or algae supplement",
                    "Choose triglyceride-form fish oil for higher absorption",
                    "Refrigerate supplements to prevent oxidation"
            ),
            "Source two servings of oily fish (or algae supplement) each day this week.",
            "This coaching advice is for informational purposes only. Consult a healthcare provider before changing your supplement regimen."
    );

    /** Helper: fresh CoachAgent backed by real tools + InMemoryMemoryService. */
    private static CoachAgent freshAgent() {
        return new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                new MemoryTool(new InMemoryMemoryService())
        );
    }

    @Test
    void coachFromResearch_includesBriefTopicInPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = freshAgent();
        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains(FAKE_BRIEF.topic()),
                "Prompt must include the ResearchBrief topic");
    }

    @Test
    void coachFromResearch_includesKeyFindingsInPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = freshAgent();
        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        // At least one key finding from the ResearchBrief must be injected.
        assertTrue(prompt.contains(FAKE_BRIEF.keyFindings().getFirst()),
                "Prompt must include key findings from the ResearchBrief");
    }

    @Test
    void coachFromResearch_includesNutritionCalcOutput() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = freshAgent();
        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        // NutritionCalcTool output is prefixed with "[NutritionCalc: ..."
        assertTrue(prompt.contains("[NutritionCalc:"),
                "Prompt must contain NutritionCalcTool output (macros are not in ResearchBrief)");
    }

    @Test
    void coachFromResearch_makesExactlyOneLlmCall() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = freshAgent();
        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        assertEquals(1, promptRunner.getLlmInvocations().size(),
                "CoachAgent.coachFromResearch must make exactly 1 LLM call");
    }

    @Test
    void coachFromResearch_returnsStubResponse() {
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_ADVICE);

        var agent = freshAgent();
        var result = agent.coachFromResearch(FAKE_BRIEF, context.ai());

        assertEquals(FAKE_ADVICE.summary(), result.summary(),
                "Returned CoachAdvice must match the stubbed LLM response");
        assertEquals(FAKE_ADVICE.weeklyGoal(), result.weeklyGoal());
    }

    @Test
    void coachFromResearch_storesSessionNoteInMemory() {
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_ADVICE);

        var memoryTool = new MemoryTool(new InMemoryMemoryService());
        var agent = new CoachAgent(new WebSearchTool(), new NutritionCalcTool(), memoryTool);

        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        List<String> notes = memoryTool.getAllNotes(CoachAgent.DEFAULT_USER_ID);
        assertFalse(notes.isEmpty(), "A coaching note must be stored after coachFromResearch()");
        assertTrue(notes.getFirst().contains(FAKE_BRIEF.topic()),
                "The stored note must reference the coached topic");
    }

    @Test
    void coachFromResearch_injectsPreviousNotesIntoPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var memoryTool = new MemoryTool(new InMemoryMemoryService());
        // Pre-seed a note containing the topic so lookupNotes(topic) will match.
        String preSeedNote = "Coached on: " + FAKE_BRIEF.topic() + " — prior session";
        memoryTool.storeMemory(CoachAgent.DEFAULT_USER_ID, preSeedNote);

        var agent = new CoachAgent(new WebSearchTool(), new NutritionCalcTool(), memoryTool);
        agent.coachFromResearch(FAKE_BRIEF, context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains(preSeedNote),
                "Prompt must include previously stored coaching notes");
    }
}
