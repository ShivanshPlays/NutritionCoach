package com.nutritioncoach.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.ResearchBrief;
import com.nutritioncoach.observability.AgentMetricsService;
import com.nutritioncoach.tool.MemoryTool;
import com.nutritioncoach.tool.NutritionCalcTool;
import com.nutritioncoach.tool.WebSearchTool;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 3: CoachAgent — personalised coaching advice backed by tools
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * What's new compared to ResearchAgent (Phase 2):
 *   Phase 2 — ResearchAgent had no tools; it only called the LLM.
 *   Phase 3 — CoachAgent pre-fetches context from THREE tools (web search,
 *              nutrition calc, memory) before calling the LLM. This enriches
 *              the prompt with grounding data and reduces hallucination.
 *
 * Tool-calling pattern used (pre-fetch / deterministic):
 *   The JAVA CODE decides which tools to call and in what order, then
 *   passes results into the LLM prompt.  This is different from "LLM-
 *   directed tool calling" where the model outputs a tool-call JSON and
 *   the framework executes it.
 *
 *   Pre-fetch pros:  fully testable, no extra round-trips, deterministic
 *   Pre-fetch cons:  agent always calls every tool even if irrelevant
 *
 *   LLM-directed tool calling (added in Phase 7/Dynamic Agents) would use
 *   Spring AI's function-calling mechanism to let Gemini decide which tools
 *   to invoke.  For Phase 3 the pre-fetch pattern is simpler and sufficient.
 *
 * MERN/Next.js analogy (Mastra with tools):
 *   // Mastra (Node.js):
 *   export const coachAgent = new Agent({
 *     name: 'CoachAgent',
 *     instructions: `You are a personalised nutrition coach...`,
 *     model: google('gemini-2.5-flash'),
 *     tools: { webSearch, nutritionCalc, memory },  ← tools listed here
 *   });
 *   // The agent.generate() call lets the LLM decide to call those tools.
 *
 *   // Embabel (Java) — pre-fetch variant (Phase 3):
 *   @Agent class CoachAgent {
 *     // Tools injected via Spring DI (constructor injection)
 *     @Action
 *     public CoachAdvice advise(UserInput input, Ai ai) {
 *       String ctx = webSearch.searchWeb(topic);  // ← Java calls the tool
 *       return ai.withDefaultLlm().createObject(prompt + ctx, CoachAdvice.class);
 *     }
 *   }
 *
 * Spring DI on an @Agent class:
 *   @Agent is meta-annotated with @Component, so Spring discovers it as a
 *   bean and can inject other @Component beans (our tools) via the
 *   constructor.  No 'new' keyword, no manual lookup needed.
 *   MERN analogy: importing services in a Next.js route handler.
 *
 * Tool injection benefits vs Phase 2:
 *   By injecting WebSearchTool, NutritionCalcTool, and MemoryTool:
 *     1. Each tool is independently tested and swappable (interface segregation)
 *     2. Tests for CoachAgent can use real tool instances (fast, deterministic)
 *     3. Phase 10 (RAG) can replace WebSearchTool in one place without
 *        changing CoachAgent's public API
 *   MERN analogy: injecting a database client or API service into a handler
 *   vs calling it inline.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   "Agents need tools to act on the world. A well-designed tool is:
 *    • Narrow: one thing done well
 *    • Idempotent/read-only where possible
 *    • Easily testable in isolation"
 *
 * Book ref: Chapter 10 — Third-Party Tools & Integrations
 *   WebSearchTool wraps an external capability (web search) behind a clean
 *   interface.  Phase 3 uses a stub; Phase 10 swaps in a real search API.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Agent(description = "Generate personalised nutrition coaching advice for a given topic, using web search, nutrition data, and memory tools")
public class CoachAgent {

    // ── Stable internal user key for single-user Phase 3 ──────────────────
    // In Phase 4 this will be replaced by the real userId from the request.
    // MERN analogy: a hardcoded 'userId: "default"' before auth is wired up.
    public static final String DEFAULT_USER_ID = "default";

    // ── Tool dependencies — injected by Spring DI ─────────────────────────
    // @Agent is meta-annotated with @Component → Spring wires these via ctor.
    // MERN analogy: const { webSearch, nutritionCalc, memory } = dependencies
    private final WebSearchTool webSearchTool;
    private final NutritionCalcTool nutritionCalcTool;
    private final MemoryTool memoryTool;

    // Phase 9: observability — wraps each tool call with timing + structured log.
    // MERN analogy: const metrics = useMetrics() injected into the agent handler.
    private final AgentMetricsService agentMetrics;

        // MERN/Next.js analogy:
        // In tests, you manually construct all dependencies (e.g., new MemoryTool(new InMemoryMemoryService())).
        // In production, Spring Boot's Dependency Injection (DI) system wires up all @Component beans automatically.
        //
        // How does Embabel/AgentInvocation know which MemoryTool to use?
        // - When you call AgentInvocation.create(...).invoke(...), Embabel asks Spring for the CoachAgent bean.
        // - Spring sees CoachAgent's constructor needs WebSearchTool, NutritionCalcTool, and MemoryTool.
        // - Spring finds the single @Component MemoryTool bean in the context.
        // - That MemoryTool bean was itself constructed with the current MemoryService implementation (JpaMemoryService in prod).
        //
        // So: in production, you do NOT manually instantiate CoachAgent or MemoryTool. Spring wires everything up using the beans defined in your config.
        // In tests, you can manually pass a test double (InMemoryMemoryService) to MemoryTool for isolation and speed.
        //
        // Book ref: Chapter 7 — Memory (backing-store abstraction)

    @Autowired
    public CoachAgent(WebSearchTool webSearchTool,
                      NutritionCalcTool nutritionCalcTool,
                      MemoryTool memoryTool,
                      AgentMetricsService agentMetrics) {
        this.webSearchTool = webSearchTool;
        this.nutritionCalcTool = nutritionCalcTool;
        this.memoryTool = memoryTool;
   

        // In production, this constructor is called by Spring with beans from the application context.
        // In tests, you call it directly with your own tool/test double instances.
        this.agentMetrics = agentMetrics;
    }

    /**
     * Test-friendly 3-arg constructor — uses a no-op metrics service.
     * Existing unit tests (CoachAgentTest) pass all 3 tools directly;
     * they continue to work without a Spring context or MeterRegistry.
     *
     * MERN analogy: default-exporting a factory: (deps) => agent
     *   where metrics default to a jest no-op when omitted.
     */
    public CoachAgent(WebSearchTool webSearchTool,
                      NutritionCalcTool nutritionCalcTool,
                      MemoryTool memoryTool) {
        this(webSearchTool, nutritionCalcTool, memoryTool, AgentMetricsService.noOp());
    }

    /**
     * Collect context from tools, then ask the LLM to synthesise personalised advice.
     *
     * Tool-calling sequence (pre-fetch pattern):
     *   1. searchWeb   → supplementary research snippets (read-only)
     *   2. calcNutrition → macro facts for foods mentioned in the topic (read-only)
     *   3. lookupNotes → previous coaching notes for this user (read-only)
     *   4. Construct enriched prompt and call LLM → CoachAdvice
     *   5. storeMemory  → record that this topic was coached (mutating — last)
     *
     * Ordering note: mutating tool calls (storeMemory) happen after the LLM call
     * so that a failure during LLM generation does not leave stale notes.
     * This mirrors the "write last" principle in safe agentic design.
     *
     * MERN analogy: the `execute` block of a Mastra tool-using agent action.
     *
     * Book ref: Chapter 6 — Tool Calling
     *   Sequencing tools before the LLM call is the "augmented context"
     *   pattern: gather facts deterministically, then synthesise with the LLM.
     *
     * @param userInput the user's nutrition question / topic
     * @param ai        Embabel's LLM gateway (injected by the platform)
     * @return structured CoachAdvice
     */
    @AchievesGoal(description = "Produce personalised CoachAdvice for the given nutrition topic")
    @Action
    public CoachAdvice advise(UserInput userInput, Ai ai) {
        String topic = userInput.getContent();

        // ── Step 1: gather supplementary web context (read-only) ──────────
        // Phase 9: wrapped with timedTool() — logs tool name, input hash, latency, status.
        // Book ref: Chapter 16 — Observability: "Log each tool call individually."
        String webContext = agentMetrics.timedTool(
                "WebSearchTool.searchWeb",
                AgentMetricsService.hashInput(topic),
                () -> webSearchTool.searchWeb(topic));

        // ── Step 2: calculate macro facts (read-only) ─────────────────────
        String nutritionData = agentMetrics.timedTool(
                "NutritionCalcTool.calculateNutrition",
                AgentMetricsService.hashInput(topic),
                () -> nutritionCalcTool.calculateNutrition(topic, "standard serving"));

        // ── Step 3: retrieve existing notes for personalisation (read-only) ─
        List<String> previousNotes = agentMetrics.timedTool(
                "MemoryTool.lookupNotes",
                AgentMetricsService.hashInput(topic),
                () -> memoryTool.lookupNotes(DEFAULT_USER_ID, topic));
        String notesContext = previousNotes.isEmpty()
                ? "No previous notes for this user on this topic."
                : "Previous coaching notes:\n" + String.join("\n- ", previousNotes);

        // ── Step 4: call the LLM with enriched prompt ─────────────────────
        //
        // The prompt follows the "seed crystal" pattern from the book:
        //   • Role definition (WHO the model should be)
        //   • Grounding data (WHAT facts to use)
        //   • Constraints (HOW to structure the response)
        //   • Anti-hallucination instruction ("base advice on provided data")
        //
        // Book ref: Chapter 3 — Writing Great Prompts
        //   Multi-section prompts with labelled data blocks reduce hallucination
        //   because the model has clear anchors to reference.
        //
        // MERN analogy: the `prompt` string passed to Vercel AI SDK generateObject().
        CoachAdvice advice = ai
                .withDefaultLlm()
                .createObject("""
                        You are a warm, knowledgeable, and personalised nutrition coach.
                        Your task is to synthesise the provided research data into
                        actionable, evidence-based coaching advice tailored to the user's topic.

                        ── USER TOPIC ────────────────────────────────────────────────────
                        %s

                        ── WEB SEARCH CONTEXT ────────────────────────────────────────────
                        %s

                        ── NUTRITION CALCULATION DATA ────────────────────────────────────
                        %s

                        ── USER HISTORY ──────────────────────────────────────────────────
                        %s

                        ── INSTRUCTIONS ──────────────────────────────────────────────────
                        Base your advice strictly on the data provided above. Do not invent
                        studies, statistics, or product claims not mentioned in the context.

                        Your response must include:
                        - summary:     2–3 sentence plain-language overview of key insights
                        - actionItems: 3–5 concrete, measurable steps the user can take
                                       starting this week (e.g. "Eat 150 g salmon 3× per week")
                        - weeklyGoal:  a single measurable target for the next 7 days
                        - disclaimer:  a brief safety reminder that this is not medical advice
                        """.formatted(topic, webContext, nutritionData, notesContext),
                        CoachAdvice.class);

        // ── Step 5: store this coaching session in memory (mutating — done last) ─
        // MERN analogy: await db.notes.insert({ userId, content }) after the LLM call.
        agentMetrics.timedTool(
                "MemoryTool.storeMemory",
                AgentMetricsService.hashInput("Coached on: " + topic),
                () -> { memoryTool.storeMemory(DEFAULT_USER_ID, "Coached on: " + topic); return null; });

        return advice;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Phase 5 — Step 2 of the two-agent pipeline
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Synthesise personalised coaching advice FROM a pre-built ResearchBrief.
     *
     * This is the second step in the Phase 5 two-agent pipeline:
     *   UserInput → ResearchAgent.gatherFacts → ResearchBrief
     *                                                   ↓
     *                             CoachAgent.coachFromResearch → CoachAdvice
     *
     * Why a separate action (not just reuse advise())?:
     *   advise() starts from raw text and pre-fetches its own web research.
     *   This action starts from a typed ResearchBrief produced by ResearchAgent —
     *   the research is already done and structured.  The prompt uses the
     *   ResearchBrief fields directly instead of the raw web search output.
     *
     * Pipeline advantage over advise():
     *   ResearchAgent can be specialised (e.g. query PubMed, use RAG), and
     *   CoachAgent focuses only on personalisation.  Separation of concerns.
     *
     * Embabel GOAP planning:
     *   When the world state contains ResearchBrief (not UserInput), Embabel's
     *   planner picks this action because its precondition (ResearchBrief present)
     *   is satisfied.  advise() requires UserInput, which is absent in step 2.
     *   MERN analogy: Mastra workflow step that receives its input from the
     *   previous step's output, not from the raw user message.
     *
     * Tools used here (vs advise):
     *   • NutritionCalcTool — still called; ResearchBrief has findings but no macros
     *   • MemoryTool.lookupNotes — personalise using prior coaching history
     *   • WebSearchTool — NOT called; research context already in ResearchBrief
     *   • MemoryTool.storeMemory — still called last (write-last principle)
     *
     * @param brief pre-built research summary from ResearchAgent
     * @param ai    Embabel's LLM gateway
     * @return structured CoachAdvice
     *
     * Book ref: Chapter 4 — Agents 101
     *   Multi-agent pipelines: "Each agent does one thing well.
     *    ResearchAgent = gather structured knowledge.
     *    CoachAgent    = personalise and contextualise that knowledge."
     *
     * Book ref: Chapter 7 — Memory (Pipeline step)
     *   The MemoryTool lookup ensures prior coaching notes are injected even
     *   in the pipeline path, preserving continuity across sessions.
     */
    @AchievesGoal(description = "Produce personalised CoachAdvice from a pre-built ResearchBrief (pipeline step 2)")
    @Action
    public CoachAdvice coachFromResearch(ResearchBrief brief, Ai ai) {
        String topic = brief.topic();

        // ── Step 1: add macro data (ResearchBrief has findings, not macros) ───
        // MERN analogy: const macros = await nutritionCalcTool.execute({ food: topic })
        String nutritionData = nutritionCalcTool.calculateNutrition(topic, "standard serving");

        // ── Step 2: retrieve previous coaching notes for personalisation ────
        // MERN analogy: const history = await memory.query({ userId, query: topic })
        List<String> previousNotes = memoryTool.lookupNotes(DEFAULT_USER_ID, topic);
        String notesContext = previousNotes.isEmpty()
                ? "No previous notes for this user on this topic."
                : "Previous coaching notes:\n- " + String.join("\n- ", previousNotes);

        // ── Step 3: build findings block from ResearchBrief ────────────────
        // This is the key difference from advise(): we use the structured brief
        // instead of a raw web search string.  The LLM gets cleaner, typed data.
        //
        // MERN analogy: template literal that destructures the ResearchBrief:
        //   `Key findings:\n${brief.keyFindings.join('\n')}`
        String researchContext = """
                Topic: %s

                Key Findings:
                %s

                Known Risks:
                %s

                Follow-up Questions (for reference):
                %s
                """.formatted(
                brief.topic(),
                String.join("\n", brief.keyFindings()),
                String.join("\n", brief.risks()),
                String.join("\n", brief.nextQuestions())
        );

        // ── Step 4: call the LLM with enriched prompt ─────────────────────
        // Same multi-section prompt pattern as advise(), but the
        // "WEB SEARCH CONTEXT" section is replaced by structured ResearchBrief data.
        //
        // Book ref: Chapter 3 — Writing Great Prompts
        //   Structured, labelled input sections reduce hallucination.
        CoachAdvice advice = ai
                .withDefaultLlm()
                .createObject("""
                        You are a warm, knowledgeable, and personalised nutrition coach.
                        You have been given a pre-researched brief from a research agent.
                        Your task is to synthesise it into actionable coaching advice
                        tailored to the user.

                        ── RESEARCH BRIEF ──────────────────────────────────────────────────
                        %s

                        ── NUTRITION CALCULATION DATA ───────────────────────────────────
                        %s

                        ── USER HISTORY ────────────────────────────────────────────────────
                        %s

                        ── INSTRUCTIONS ────────────────────────────────────────────────────
                        Base your advice strictly on the research brief and nutrition
                        data above. Do not invent studies or statistics not mentioned.

                        Your response must include:
                        - summary:     2–3 sentence plain-language overview
                        - actionItems: 3–5 concrete, measurable steps the user can take
                                       starting this week
                        - weeklyGoal:  a single measurable target for the next 7 days
                        - disclaimer:  a brief safety reminder (not medical advice)
                        """.formatted(researchContext, nutritionData, notesContext),
                        CoachAdvice.class);

        // ── Step 5: store coaching session (mutating — write last) ─────────
        // MERN analogy: await memory.save({ userId, content: 'Coached on: ...' })
        memoryTool.storeMemory(DEFAULT_USER_ID, "Coached on (pipeline): " + topic);

        return advice;
    }
}
