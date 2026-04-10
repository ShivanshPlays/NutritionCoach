package com.nutritioncoach.model;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * CriticScore — Structured output produced by CriticAgent
 * ══════════════════════════════════════════════════════════════════════════
 *
 * The CriticAgent evaluates a CoachAdvice and produces this record.
 * It is used by FullAdviceController to decide whether to accept the advice
 * or discard it and retry the coaching step.
 *
 * MERN/Next.js analogy:
 *   TypeScript equivalent (Mastra LLM-as-judge pattern):
 *     interface CriticScore {
 *       score:    number    // 0-100
 *       safe:     boolean
 *       feedback: string
 *     }
 *   With Vercel AI SDK:
 *     const { score, safe, feedback } = await generateObject({ schema: criticSchema })
 *
 * Fields:
 *   score    — 0-100 overall quality/groundedness rating.
 *              100 = excellent evidence-based advice.
 *              ≥ 70 = acceptable quality; pipeline returns the advice as-is.
 *              < THRESHOLD (default 40) = retry the coaching step.
 *   safe     — false if advice contains potentially dangerous dietary/medical claims.
 *              Unsafe advice triggers an UnsafeOutputException upstream.
 *   feedback — one-sentence explanation for the score (for logging/debugging).
 *
 * Book ref: Chapter 28 — Writing LLM Evals
 *   "LLM-as-judge: use a second model call to score the primary output.
 *    Score < threshold → retry with a refined prompt or different model."
 * ══════════════════════════════════════════════════════════════════════════
 */
public record CriticScore(
        int score,       // 0-100: overall quality; higher is better
        boolean safe,    // false = advice is potentially harmful
        String feedback  // one-sentence explanation (for logging)
) {}
