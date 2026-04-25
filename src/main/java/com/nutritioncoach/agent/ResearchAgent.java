package com.nutritioncoach.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.ResearchBrief;
import com.nutritioncoach.observability.AgentMetricsService;
import com.nutritioncoach.rag.RetrievalTool;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 2: First Embabel Agent — ResearchAgent
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * What changed from Phase 1:
 *   Phase 1 — ResearchController called ChatClient directly (one big blob).
 *   Phase 2 — Logic extracted into a proper @Agent class; the controller
 *              delegates via AgentPlatform.  Each discrete step is an @Action.
 *
 * MERN/Next.js analogy:
 *   This file is the Embabel equivalent of a Mastra Agent:
 *
 *     // Mastra (Node.js):
 *     export const researchAgent = new Agent({
 *       name: 'ResearchAgent',
 *       instructions: `You are an expert nutritionist...`,
 *       model: google('gemini-2.5-flash'),
 *       tools: [],
 *     });
 *
 *     // Embabel (Java):
 *     @Agent(description = "Research a nutrition topic → ResearchBrief")
 *     public class ResearchAgent { @Action ... }
 *
 *   Key difference: Embabel uses GOAP (Goal-Oriented Action Planning) to
 *   automatically figure out which @Action methods to run, in what order,
 *   based on their input/output types — you don't wire steps manually.
 *   This is similar to how Mastra workflows chain steps, but the sequencing
 *   is inferred from type signatures rather than declared explicitly.
 *
 * @Agent — class-level stereotype:
 *   Tells Embabel to register this class as an agent.  The `description`
 *   is used by the platform when choosing between multiple agents in
 *   open/closed mode (like Mastra's `name` + `instructions` for routing).
 *
 * @Action — method-level:
 *   Declares a discrete step the agent can take.  Embabel infers:
 *     • Precondition  → the method's domain object parameters must be in the
 *                        "world state" (blackboard) before this can run.
 *     • Postcondition → the return type is added to the blackboard.
 *   MERN analogy: a single `step()` inside a Mastra workflow.
 *
 * @AchievesGoal — marks the terminal @Action:
 *   When this action completes, the agent is done.  The platform returns
 *   control to the caller (our ResearchController).
 *   MERN analogy: the final step in a workflow that produces the output.
 *
 * Ai ai parameter:
 *   Embabel injects this infrastructure type into every @Action method.
 *   It is NOT a domain object (doesn't drive planning); it's the LLM gateway.
 *   MERN analogy: the `Mastra` instance / Vercel AI SDK's `generateObject`.
 *
 *   ai.withDefaultLlm() → picks the LLM configured in embabel.models.default-llm
 *   .createObject(prompt, Class) → structured output; same as generateObject+Zod.
 *
 * Book ref: Chapter 4 — Agents 101
 *   "An agent = LLM + memory + tools + goal."
 *   This is the simplest possible agent: LLM only, no memory, no tools yet.
 *   The goal ("produce a ResearchBrief") is declared via @AchievesGoal.
 *   Tools are added in Phase 3.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Agent(description = "Research a nutrition topic and produce a structured ResearchBrief with key findings, risks, and follow-up questions")
public class ResearchAgent {

    // ── Phase 10: RAG retrieval — injected to pre-fetch grounding context ──
    // MERN analogy: const retrieval = useRetrieval() injected into the agent handler.
    // Book ref: Chapter 20 — RAG: Synthesis
    //   "Inject retrieved context in a clearly labelled section of the prompt."
    private final RetrievalTool retrievalTool;

    // ── Phase 9: Observability — wraps retrieval call with timing + MDC ───
    private final AgentMetricsService agentMetrics;

    /**
     * Spring-managed constructor — wires real RetrievalTool + AgentMetricsService.
     * @Autowired needed because this class now has two constructors.
     * MERN analogy: constructor-injected deps in a NestJS @Injectable() service.
     */
    @Autowired
    public ResearchAgent(RetrievalTool retrievalTool, AgentMetricsService agentMetrics) {
        this.retrievalTool = retrievalTool;
        this.agentMetrics = agentMetrics;
    }

    /**
     * Test-friendly no-arg constructor — uses no-op stubs so existing
     * ResearchAgentTest can call {@code new ResearchAgent()} without wiring
     * a VectorStore or MeterRegistry.
     * MERN analogy: default-exporting a factory where retrieval + metrics
     *   default to jest no-ops when omitted.
     */
    public ResearchAgent() {
        this(RetrievalTool.noOp(), AgentMetricsService.noOp());
    }

    /**
     * Single action: take the user's raw topic text and produce a typed
     * ResearchBrief via a structured LLM call.
     *
     * Input condition (inferred from parameter types):
     *   A UserInput must be in the blackboard — provided by the controller call.
     *
     * Output condition (inferred from return type):
     *   After this runs, ResearchBrief is in the blackboard.
     *   Because it is also @AchievesGoal, the agent process completes.
     *
     * MERN analogy: Vercel AI SDK generateObject():
     *   const { object } = await generateObject({
     *     model, schema: researchBriefSchema,
     *     prompt: `Research the following nutrition topic: ${topic}`
     *   })
     *
     * Note: no constructor injection needed here — this agent has no Spring
     * service dependencies.  Ai is injected by Embabel at invocation time,
     * not at Spring context wiring time.
     */
    @AchievesGoal(description = "Produce a structured ResearchBrief for the given nutrition topic")
    @Action
    public ResearchBrief gatherFacts(UserInput userInput, Ai ai) {
        String topic = userInput.getContent();

        // ── Phase 10: RAG retrieval — fetch grounding context before LLM call ──
        //
        // Pre-fetch pattern: Java explicitly calls the tool, then injects the
        // result into the prompt.  Same pattern used in CoachAgent (Phase 3).
        //
        // When the VectorStore is empty (no documents ingested yet), retrieveContext()
        // returns "" — the prompt section becomes empty but the LLM still works.
        //
        // Phase 9: timedTool() wraps retrieval with Micrometer timer + MDC.
        // Book ref: Chapter 19 — RAG: Retrieval & Reranking
        //   "At retrieval time, query with the same embedding model used at
        //    ingestion time — mismatched models destroy similarity scores."
        //
        // MERN analogy:
        //   const context = await retrievalTool.retrieveContext(topic)
        String retrievedContext = agentMetrics.timedTool(
                "RetrievalTool.retrieveContext",
                AgentMetricsService.hashInput(topic),
                () -> retrievalTool.retrieveContext(topic));

        // Decide what to show in the context section of the prompt.
        // If no docs were ingested, we tell the model explicitly so it doesn't
        // hallucinate a retrieval source.
        String ragSection = retrievedContext.isBlank()
                ? "No documents have been ingested yet. Base your research on general knowledge."
                : retrievedContext;

        // ── LLM call with enriched prompt ─────────────────────────────────
        //
        // The ── RETRIEVED CONTEXT ── section is the RAG synthesis step.
        // The model is instructed to PREFER retrieved facts over general knowledge,
        // which reduces hallucination when documents are available.
        //
        // .createObject() = structured output. Embabel inspects ResearchBrief
        // via reflection, builds a JSON schema, appends format instructions to
        // the prompt, calls the LLM, and deserialises the response.
        // Exactly equivalent to Spring AI's .call().entity(ResearchBrief.class)
        // or Vercel AI SDK's generateObject({schema: researchBriefSchema}).
        //
        // Book ref: Chapter 5 — Structured Output
        //   Structured output makes agentic pipelines reliable; open text makes
        //   downstream parsing fragile.
        // Book ref: Chapter 20 — RAG: Synthesis
        //   "Inject retrieved context in a clearly labelled section of the prompt
        //    so the model knows exactly which facts to use."
        return ai
                .withDefaultLlm()
                .createObject("""
                        You are an expert nutritionist and research analyst.
                        Research the following nutrition topic thoroughly and provide a
                        comprehensive, evidence-based analysis.

                        ── TOPIC ───────────────────────────────────────────────────────────────────
                        %s

                        ── RETRIEVED CONTEXT (from ingested documents) ───────────────────
                        %s

                        ── INSTRUCTIONS ─────────────────────────────────────────────────────────
                        When retrieved context is available, PREFER those facts over general
                        knowledge. Clearly synthesise findings from the retrieved documents.
                        When no context is provided, use evidence-based general knowledge.

                        Your response must include:
                        - Key nutritional findings (scientific facts, mechanisms, benefits)
                        - Potential risks or contraindications (side effects, interactions,
                          populations that should avoid it)
                        - Suggested follow-up questions for deeper investigation
                        """.formatted(topic, ragSection),
                        ResearchBrief.class);
    }
}
