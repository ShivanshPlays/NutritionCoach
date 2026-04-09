package com.nutritioncoach.model;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * CoachAdvice — Structured output produced by CoachAgent
 * ══════════════════════════════════════════════════════════════════════════
 *
 * MERN/Next.js analogy:
 *   TypeScript equivalent:
 *     interface CoachAdvice {
 *       summary:     string
 *       actionItems: string[]
 *       weeklyGoal:  string
 *       disclaimer:  string
 *     }
 *   With Vercel AI SDK you'd pass a matching Zod schema to generateObject():
 *     const schema = z.object({
 *       summary:     z.string(),
 *       actionItems: z.array(z.string()),
 *       weeklyGoal:  z.string(),
 *       disclaimer:  z.string(),
 *     })
 *
 * Design considerations (tool-calling phase):
 *   • summary     — plain-language synthesis of research + tool outputs
 *   • actionItems — concrete, measurable steps the user can act on today
 *   • weeklyGoal  — single measurable target for the 7-day horizon
 *   • disclaimer  — safety reminder (not a substitute for medical advice)
 *
 * Book ref: Chapter 5 — Structured Output
 *   Structured output is especially important when tool call results need
 *   to be combined: the LLM can hallucinate less when forced into a schema.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   CoachAdvice is the terminal output produced after CoachAgent has gathered
 *   context from multiple tools and synthesised it via a single LLM call.
 * ══════════════════════════════════════════════════════════════════════════
 */
public record CoachAdvice(
        String summary,
        List<String> actionItems,
        String weeklyGoal,
        String disclaimer
) {}
