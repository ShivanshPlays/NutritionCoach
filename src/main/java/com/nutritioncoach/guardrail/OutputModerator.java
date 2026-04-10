package com.nutritioncoach.guardrail;

import com.nutritioncoach.model.CoachAdvice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.joining;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * OutputModerator — Flag potentially unsafe advice before sending to client
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Scans all text fields of a CoachAdvice for patterns that indicate the LLM
 * has produced dangerous dietary or medical claims.  If found, throws
 * UnsafeOutputException which the controller translates to HTTP 422.
 *
 * Why output moderation?
 *   Even with a carefully crafted system prompt, LLMs can occasionally
 *   hallucinate harmful advice:
 *     • "Eat only 300 calories per day to lose weight fast."
 *     • "You can stop your diabetes medication if you follow this diet."
 *     • "This will cure your cancer."
 *   These statements, if delivered to users of a nutrition app, could cause
 *   serious physical harm.
 *
 * What is checked:
 *   UNSAFE_PATTERNS list covers:
 *     1. Specific medical claims — "cure/treat/diagnose your [condition]"
 *     2. Medication tampering   — "stop taking your [medication]"
 *     3. Dangerously low calorie targets — "eat fewer than 5xx calories"
 *
 * Limitations:
 *   Pattern matching catches obvious unsafe language but is not a complete
 *   safety solution.  CriticAgent (also in Phase 6) provides a semantic
 *   second opinion via the LLM itself for subtler issues.
 *
 * MERN/Next.js analogy:
 *   Equivalent of a response interceptor in a Mastra agent that runs
 *   before the output is returned to the client:
 *
 *     export const coachAgent = new Agent({
 *       afterGenerate: async (result) => {
 *         if (isMedicalClaim(result.text)) throw new SafetyViolation()
 *       }
 *     })
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Output moderation is the last line of defence. Never ship an agent
 *    without at least a keyword-level safety check on sensitive domains."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class OutputModerator {

    // ── Unsafe output patterns ────────────────────────────────────────────────
    // OWASP LLM Top 10: LLM08 — Excessive Agency and unsafe output.
    private static final List<Pattern> UNSAFE_PATTERNS = List.of(
            // Medical treatment claims — "cure/treat/diagnose your [condition]"
            Pattern.compile(
                    "\\b(cure|treat|diagnose|prescribe|medicate)s?\\b.{0,30}\\b(cancer|diabetes|disease|condition|disorder|illness)\\b",
                    CASE_INSENSITIVE),
            // Medication tampering — "stop taking your medication" / "stop taking your prescription drugs"
            Pattern.compile(
                    "\\bstop\\s+(?:taking\\s+)?(?:your\\s+)?(medication|prescription|insulin|drug|pill)s?\\b",
                    CASE_INSENSITIVE),
            // Dangerously low calorie target — "eat only 300 / fewer than 499 calories"
            Pattern.compile(
                    "(?:eat|consume|limit|only|fewer\\s+than|less\\s+than)\\s+(?:[1-4]\\d{2})\\s+cal",
                    CASE_INSENSITIVE)
    );

    /**
     * Check a CoachAdvice for unsafe content.
     * All text fields (summary, actionItems, weeklyGoal, disclaimer) are checked.
     *
     * @param advice the CoachAdvice to moderate
     * @throws UnsafeOutputException if any field contains an unsafe pattern
     */
    public void check(CoachAdvice advice) {
        if (advice == null) return;

        // Concatenate all text fields for a single-pass check.
        // MERN analogy: JSON.stringify(advice) then scan the whole string.
        String allText = Stream.of(
                advice.summary(),
                advice.weeklyGoal(),
                advice.disclaimer(),
                advice.actionItems() == null ? "" : String.join(" ", advice.actionItems())
        ).collect(joining(" "));

        for (Pattern pattern : UNSAFE_PATTERNS) {
            if (pattern.matcher(allText).find()) {
                throw new UnsafeOutputException(
                        "Advice moderation failed: potentially unsafe content detected — review before delivery");
            }
        }
    }
}
