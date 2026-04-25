package com.nutritioncoach.model;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * RouteAdviceResponse — wraps CoachAdvice with routing metadata
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Returned by POST /api/route-advice.  Includes the advice itself plus
 * metadata about which route was taken and which model was selected.
 * This makes routing decisions visible to clients and useful for debugging.
 *
 * MERN/Next.js analogy:
 *   type RouteAdviceResponse = {
 *     advice:    CoachAdvice
 *     tier:      'FREE' | 'PREMIUM'
 *     route:     'SINGLE_STEP' | 'FULL_PIPELINE'
 *     model:     string   // e.g. 'gemini-2.0-flash'
 *   }
 *
 * Why expose routing metadata to the client?
 *   In a learning project, visibility into the routing decision is valuable:
 *     • Debug which path was chosen for a given query + tier
 *     • Build UI that shows "Research-backed result" badge for FULL_PIPELINE
 *     • Phase 13 (evals) can assert the correct route was selected
 *   A production system might strip this from the response for security.
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "The routing decision itself is data — log it, expose it, evaluate it."
 * ══════════════════════════════════════════════════════════════════════════
 */
public record RouteAdviceResponse(

        /** The coaching advice produced by the selected agent path. */
        CoachAdvice advice,

        /**
         * Tier name used for this request (FREE or PREMIUM).
         * MERN analogy: req.user.plan in an Express middleware.
         */
        String tier,

        /**
         * Route taken: SINGLE_STEP (CoachAgent only) or
         * FULL_PIPELINE (ResearchAgent → CoachAgent).
         * MERN analogy: which Mastra workflow path was triggered.
         */
        String route,

        /**
         * Name of the Gemini model selected for this tier.
         * Logged for observability; actual per-request model switching
         * requires Embabel-level API (Phase 11 will wire this fully).
         *
         * Book ref: Chapter 2 — Choosing a Provider & Model
         *   "Externalise model names so you can swap without code changes."
         */
        String model
) {}
