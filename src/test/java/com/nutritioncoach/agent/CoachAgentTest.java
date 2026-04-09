package com.nutritioncoach.agent;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.memory.InMemoryMemoryService;
import com.nutritioncoach.tool.MemoryTool;
import com.nutritioncoach.tool.NutritionCalcTool;
import com.nutritioncoach.tool.WebSearchTool;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit test for CoachAgent
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   Equivalent of a Jest unit test for a Mastra agent action that uses tools:
 *
 *     jest.mock('../tools/webSearch')
 *     jest.mock('../tools/nutritionCalc')
 *     jest.mock('../tools/memory')
 *     it('passes topic and tool data to the LLM prompt', async () => {
 *       mockWebSearch.mockReturnValue('...')
 *       mockAI.mockResolvedValue(fakeAdvice)
 *       await coachAgent.advise({ topic: 'creatine' })
 *       expect(capturedPrompt).toContain('creatine')
 *       expect(capturedPrompt).toContain('[WebSearch')     // ← tool output injected
 *       expect(capturedPrompt).toContain('[NutritionCalc') // ← tool output injected
 *     })
 *
 * Why real tool instances instead of mocks?
 *   The three tools (WebSearchTool, NutritionCalcTool, MemoryTool) are pure,
 *   deterministic, and fast (no I/O, no network).  Using real instances:
 *     • Tests actual integration between CoachAgent and its tools
 *     • No fragile mock setup to maintain
 *     • Faster than creating mocks with Mockito
 *   MERN analogy: using the actual tool `execute` function instead of
 *   jest.fn() when the function has no side-effects.
 *
 * What FakeOperationContext stubs:
 *   Only the LLM call (ai.withDefaultLlm().createObject(...)) is faked.
 *   All tool calls go through real Java implementations.
 *
 * Testing strategy:
 *   1. The topic appears in the outgoing LLM prompt (agent wires input correctly)
 *   2. Tool output markers appear in the prompt (tools were called and injected)
 *   3. Exactly 1 LLM call was made (no accidental duplicates)
 *   4. The returned CoachAdvice matches the stubbed LLM response
 *   5. MemoryTool received a note after the call (side-effect verified)
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "Test that tool outputs appear in the prompt sent to the LLM. If the
 *   tool was called but its result was silently dropped, the LLM cannot
 *   use it — the most common tool-wiring bug."
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   Unit tests are the I/O contract layer: confirm the agent calls the
 *   LLM with correctly enriched inputs before measuring output quality.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class CoachAgentTest {

    // Pre-built stub that the fake LLM will "return"
    private static final CoachAdvice FAKE_ADVICE = new CoachAdvice(
            "Creatine monohydrate is the most studied sports supplement with strong evidence for strength gains.",
            List.of(
                    "Take 3–5 g creatine monohydrate daily (no loading phase required)",
                    "Consume creatine with a carbohydrate source to enhance muscle uptake",
                    "Stay well-hydrated (35 ml/kg/day) when supplementing"
            ),
            "Add creatine monohydrate to your post-workout shake every day this week.",
            "This advice is for informational purposes only and does not substitute for medical advice."
    );

    @Test
    void advise_includesTopicInLlmPrompt() {
        // Arrange ─────────────────────────────────────────────────────────
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                new MemoryTool(new InMemoryMemoryService())
        );
        String topic = "creatine supplementation";

        // Act ─────────────────────────────────────────────────────────────
        var result = agent.advise(
                new UserInput(topic, Instant.now()),
                context.ai()
        );

        // Assert — return value ────────────────────────────────────────────
        assertEquals(FAKE_ADVICE.summary(), result.summary(),
                "Returned advice should match the stubbed LLM response");

        // Assert — prompt contains the topic ──────────────────────────────
        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains(topic),
                "LLM prompt must contain the user topic");
    }

    @Test
    void advise_injectsWebSearchOutputIntoPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                new MemoryTool(new InMemoryMemoryService())
        );

        agent.advise(new UserInput("creatine performance", Instant.now()), context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();

        // WebSearchTool stub output is prefixed with "[WebSearch: ..."
        assertTrue(prompt.contains("[WebSearch:"),
                "Prompt must contain the WebSearchTool output marker");
    }

    @Test
    void advise_injectsNutritionCalcOutputIntoPrompt() {
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                new MemoryTool(new InMemoryMemoryService())
        );

        agent.advise(new UserInput("salmon meals", Instant.now()), context.ai());

        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();

        // NutritionCalcTool output is prefixed with "[NutritionCalc: ..."
        assertTrue(prompt.contains("[NutritionCalc:"),
                "Prompt must contain the NutritionCalcTool output marker");
    }

    @Test
    void advise_makesExactlyOneLlmCall() {
        // Arrange
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                new MemoryTool(new InMemoryMemoryService())
        );

        // Act
        agent.advise(new UserInput("omega-3", Instant.now()), context.ai());

        // Assert
        assertEquals(1, promptRunner.getLlmInvocations().size(),
                "CoachAgent must make exactly 1 LLM call per advise() invocation");
    }

    @Test
    void advise_storesSessionNoteInMemory() {
        // Arrange
        var context = FakeOperationContext.create();
        context.expectResponse(FAKE_ADVICE);

        var memoryTool = new MemoryTool(new InMemoryMemoryService());
        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                memoryTool
        );

        // Act
        agent.advise(new UserInput("magnesium sleep", Instant.now()), context.ai());

        // Assert — memory side-effect
        List<String> notes = memoryTool.getAllNotes(CoachAgent.DEFAULT_USER_ID);
        assertFalse(notes.isEmpty(), "MemoryTool must have received a note after advise()");
        assertTrue(notes.getFirst().contains("magnesium sleep"),
                "The stored note must reference the coached topic");
    }

    @Test
    void advise_previousNotesIncludedInPrompt_whenPresent() {
        // Arrange: pre-seed a note whose text contains the exact topic string
        // so that lookupNotes(userId, topic) matches it via substring search.
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        context.expectResponse(FAKE_ADVICE);

        var memoryTool = new MemoryTool(new InMemoryMemoryService());
        String topic = "omega-3 dosing";
        // The stored note must contain the topic as a substring for lookupNotes to match it.
        String preSeedNote = "Coached on: " + topic + " — previous session";
        memoryTool.storeMemory(CoachAgent.DEFAULT_USER_ID, preSeedNote);

        var agent = new CoachAgent(
                new WebSearchTool(),
                new NutritionCalcTool(),
                memoryTool
        );

        // Act: ask about the same topic — the previous note should be retrieved
        agent.advise(new UserInput(topic, Instant.now()), context.ai());

        // Assert — previous note injected into the LLM prompt
        String prompt = promptRunner.getLlmInvocations().getFirst()
                .getMessages().getFirst().getContent();
        assertTrue(prompt.contains(preSeedNote),
                "Prompt must include previously stored notes for this topic");
    }
}
