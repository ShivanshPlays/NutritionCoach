package com.nutritioncoach.model;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * LoggerInput — input domain object for LoggerAgent
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Phase 8: passed as the initial world state for a LoggerAgent invocation.
 * Contains the userId (for writing back to memory) and the last N conversation
 * turns formatted as "role: content" strings.
 *
 * Why a dedicated record instead of passing loose parameters?
 *   Embabel's GOAP planner maps inputs and outputs by TYPE.  Wrapping the
 *   data in a typed record makes LoggerInput unambiguous in the blackboard.
 *   MERN analogy: the typed input object passed to a Mastra step:
 *     type LoggerInput = { userId: string; recentMessages: string[] }
 *
 * MERN/Next.js analogy:
 *   type LoggerInput = {
 *     userId:        string
 *     recentMessages: string[]   // formatted: "user: How much protein..."
 *   }
 *
 * Book ref: Chapter 7 — Memory
 *   "Feed recent conversation turns to the Logger agent so it can extract
 *    preferences and key facts without keeping a full context window."
 * ══════════════════════════════════════════════════════════════════════════
 */
public record LoggerInput(
        // The user whose conversation is being processed.
        // Used by LoggerService to write notes and update UserProfile.
        String userId,

        // Last N conversation turns formatted as "role: content".
        // E.g. "user: What protein sources are best for vegans?"
        //      "assistant: Great question! Legumes, tofu..."
        // MERN analogy: messages: CoreMessage[] from Vercel AI SDK
        List<String> recentMessages
) {}
