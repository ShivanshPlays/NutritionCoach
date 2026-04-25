package com.nutritioncoach.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.nutritioncoach.model.LoggerInput;
import com.nutritioncoach.model.LoggerSummary;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * Phase 8: LoggerAgent — memory lifecycle management
 * ══════════════════════════════════════════════════════════════════════════
 *
 * What this agent does:
 *   Receives the last N conversation turns (as formatted strings) and
 *   produces a LoggerSummary via a structured LLM call.  It does NOT
 *   touch the database — that is LoggerService's responsibility.
 *   The agent has exactly one concern: compress and extract signal from
 *   conversation history using the LLM.
 *
 * Separation of concerns:
 *   LoggerAgent  → "what did the user say / want?" (LLM concern)
 *   LoggerService → "where do we store it?"          (persistence concern)
 * This mirrors the Single Responsibility Principle and Mastra's pattern of
 * separate agent + workflow step.
 *
 * Why not call MemoryService inside the agent?
 *   Agents should be pure LLM orchestration.  Injecting JPA repositories
 *   into an @Agent couples the LLM layer to the persistence layer, making
 *   both harder to test.  LoggerService (the caller) owns persistence.
 *   MERN analogy: a Mastra step that calls generateObject(), not the DB.
 *
 * Invocation context:
 *   LoggerService invokes this agent asynchronously (@Async) after an
 *   advice-generation request completes, so it never blocks the HTTP
 *   response seen by the caller.
 *
 * Output (LoggerSummary) contains:
 *   • compressedSummary     — 1-2 sentence session recap
 *   • extractedGoals        — dietary goals mentioned by user
 *   • extractedRestrictions — restrictions/intolerances mentioned
 *   • keyFacts              — bullet-point facts worth persisting
 *
 * MERN/Next.js analogy:
 *   // Mastra (Node.js):
 *   export const loggerAgent = new Agent({
 *     name: 'LoggerAgent',
 *     instructions: 'Summarise the conversation and extract user preferences.',
 *     model: google('gemini-2.5-flash'),
 *   })
 *   const summary = await loggerAgent.generate(recentMessages)
 *
 *   // Embabel (Java):
 *   @Agent class LoggerAgent {
 *     @Action @AchievesGoal
 *     LoggerSummary summarise(LoggerInput input, Ai ai) { ... }
 *   }
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Assign memory lifecycle work to a dedicated agent that runs
 *    asynchronously, so it doesn't add latency to the user-facing call."
 *
 * Book ref: Chapter 7 — Memory
 *   "Don't store raw transcripts at scale — have an agent compress them
 *    into structured facts before writing to the knowledge store."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Agent(description = "Summarise recent conversation history and extract user dietary preferences, restrictions, and key facts for long-term memory")
public class LoggerAgent {

    /**
     * Compress the conversation window and extract user preferences.
     *
     * Input condition (inferred from parameter types):
     *   LoggerInput must exist in the Embabel blackboard.
     *
     * Postcondition:
     *   LoggerSummary is placed in the blackboard (and returned to LoggerService).
     *
     * @param input recent conversation turns + userId
     * @param ai    Embabel LLM gateway (injected by the platform at runtime)
     * @return structured LoggerSummary parsed from the LLM response
     *
     * MERN analogy: const summary = await ai.generateObject({ schema, prompt })
     * Book ref: Chapter 5 — Structured Output
     */
    @Action
    @AchievesGoal(description = "Produce a LoggerSummary by compressing recent conversation history and extracting user preferences and key facts")
    public LoggerSummary summariseHistory(LoggerInput input, Ai ai) {
        // Build a prompt that asks the LLM to analyse the messages.
        // The conversation turns are embedded as-is; they are pre-formatted
        // by LoggerService as "role: content" strings (safe — no user data reaches
        // this prompt without going through InputSanitiser first).
        //
        // MERN analogy: the prompt string passed to generateObject() in a Mastra step.
        String conversation = String.join("\n", input.recentMessages());

        String prompt = """
                You are a memory assistant for a nutrition coaching application.

                Analyse the following recent conversation turns and produce a structured summary.

                Conversation:
                %s

                Extract:
                1. A concise 1-2 sentence summary of what the user was asking about.
                2. Any dietary GOALS the user mentioned (e.g. "lose weight", "build muscle").
                3. Any dietary RESTRICTIONS or intolerances (e.g. "lactose intolerant", "allergic to nuts").
                4. Discrete KEY FACTS worth remembering (e.g. "eats 1800 kcal/day", "vegan since 2020").

                Return only valid JSON matching the schema. Use empty string for missing fields and empty array for keyFacts if none found.
                """.formatted(conversation);

        // ai.withDefaultLlm().createObject() maps to Vercel AI SDK generateObject():
        //   const { object } = await generateObject({ model, schema, prompt })
        //
        // Book ref: Chapter 5 — Structured Output
        //   createObject drives the LLM to produce JSON matching the target record.
        return ai.withDefaultLlm().createObject(prompt, LoggerSummary.class);
    }
}
