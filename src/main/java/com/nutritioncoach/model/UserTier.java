package com.nutritioncoach.model;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * UserTier — user subscription tier controlling routing + model selection
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Phase 7: introduces tier-based dynamic routing.  AgentRouter uses this
 * to decide which agent path to run (single-step vs full pipeline) and
 * which Gemini model name to log/select.
 *
 * MERN/Next.js analogy (Mastra dynamic routing):
 *   type UserTier = 'FREE' | 'PREMIUM'
 *   const route = tier === 'PREMIUM' && isLong ? 'fullPipeline' : 'singleStep'
 *
 * Storage:
 *   Stored as VARCHAR in user_profile.tier (Flyway V3 migration).
 *   Phase 8 (LoggerAgent) can upgrade FREE → PREMIUM based on usage patterns.
 *
 * Model mapping (from application.yml):
 *   FREE    → app.agent.model.free    (gemini-2.0-flash  — faster, cheaper)
 *   PREMIUM → app.agent.model.premium (gemini-1.5-pro    — more capable)
 *
 * Routing mapping (AgentRouter):
 *   FREE    + any query    → SINGLE_STEP  (cost control)
 *   PREMIUM + short query  → SINGLE_STEP  (speed, topic is simple enough)
 *   PREMIUM + long query   → FULL_PIPELINE (research quality for complex topics)
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Dynamic agents change their instructions, tools, or model at runtime
 *    based on context.  User tier is the simplest form of context."
 * ══════════════════════════════════════════════════════════════════════════
 */
public enum UserTier {

    /**
     * Free tier: single-step CoachAgent path, cheaper/faster model.
     * MERN analogy: a customer on the 'hobby' plan in your SaaS.
     */
    FREE,

    /**
     * Premium tier: full ResearchAgent → CoachAgent pipeline for long queries,
     * more capable model.
     * MERN analogy: a customer on the 'pro' plan in your SaaS.
     */
    PREMIUM
}
