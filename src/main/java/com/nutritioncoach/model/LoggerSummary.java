package com.nutritioncoach.model;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * LoggerSummary — structured output produced by LoggerAgent
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Phase 8: LoggerAgent reads recent conversation turns and populates this
 * record.  LoggerService then persists the extracted intelligence:
 *   • compressedSummary → saved as an AgentNote (type "summary")
 *   • extractedGoals    → merged into UserProfile.dietaryGoals
 *   • extractedRestrictions → merged into UserProfile.restrictions
 *   • keyFacts          → each saved as an AgentNote (type "fact")
 *
 * Fields are nullable-safe: LoggerAgent should produce empty strings / empty
 * lists rather than nulls so LoggerService can skip writes without NPEs.
 *
 * MERN/Next.js analogy:
 *   type LoggerSummary = {
 *     compressedSummary:    string    // 1-2 sentence summary of the session
 *     extractedGoals:       string    // e.g. "Lose 5 kg, increase protein"
 *     extractedRestrictions: string  // e.g. "Lactose intolerant, no gluten"
 *     keyFacts:             string[] // e.g. ["eats 1800 kcal/day", "vegan"]
 *   }
 *
 * Book ref: Chapter 7 — Memory
 *   "Use a dedicated Logger agent to extract signal from noise.
 *    Store compressed facts, not raw transcripts."
 * ══════════════════════════════════════════════════════════════════════════
 */
public record LoggerSummary(

        // 1-2 sentence plain-English summary of the conversation session.
        // Stored as an agent_note of type "summary".
        // MERN analogy: summary field returned by Mastra's summarize() step.
        String compressedSummary,

        // Dietary goals extracted from the conversation.
        // E.g. "Build lean muscle, reduce visceral fat."
        // Written into UserProfile.dietaryGoals (merged, not overwritten).
        String extractedGoals,

        // Dietary restrictions mentioned by the user.
        // E.g. "Lactose intolerant, no nuts."
        // Written into UserProfile.restrictions.
        String extractedRestrictions,

        // Discrete facts worth remembering across sessions.
        // Each becomes an individual agent_note (type "fact") for the user.
        // MERN analogy: Array<string> of bullet-point insights.
        List<String> keyFacts

) {}
