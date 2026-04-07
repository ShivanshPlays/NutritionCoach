package com.nutritioncoach.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.ResearchBrief;

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
        // ai.withDefaultLlm() uses the model set in embabel.models.default-llm
        // (see application.yml). For us that is gemini-2.5-flash via
        // Google AI Studio's OpenAI-compatible endpoint.
        //
        // .createObject() = structured output.  Embabel inspects ResearchBrief
        // via reflection, builds a JSON schema, appends format instructions to
        // the prompt, calls the LLM, and deserialises the response.
        // Exactly equivalent to Spring AI's .call().entity(ResearchBrief.class)
        // or Vercel AI SDK's generateObject({schema: researchBriefSchema}).
        //
        // Book ref: Chapter 5 — Structured Output
        //   Structured output makes agentic pipelines reliable; open text makes
        //   downstream parsing fragile.
        return ai
                .withDefaultLlm()
                .createObject("""
                        You are an expert nutritionist and research analyst.
                        Research the following nutrition topic thoroughly and provide a
                        comprehensive, evidence-based analysis.

                        Topic: %s

                        Your response must include:
                        - Key nutritional findings (scientific facts, mechanisms, benefits)
                        - Potential risks or contraindications (side effects, interactions,
                          populations that should avoid it)
                        - Suggested follow-up questions for deeper investigation
                        """.formatted(userInput.getContent()),
                        ResearchBrief.class);
    }
}
