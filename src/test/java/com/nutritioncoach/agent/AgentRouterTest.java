package com.nutritioncoach.agent;

import com.nutritioncoach.agent.AgentRouter.Route;
import com.nutritioncoach.agent.AgentRouter.RouteDecision;
import com.nutritioncoach.model.UserTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Unit tests for AgentRouter.classify()
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * classify() is the pure routing-logic method — no LLM, no Spring context.
 * We construct AgentRouter with a null AgentPlatform because classify()
 * never uses it; only route() (the integration entry point) does.
 *
 * Testing strategy:
 *   • Cover every cell in the routing table (tier × query length)
 *   • Verify model names are mapped to the right tier
 *   • Verify threshold boundary conditions (at / just below SHORT_QUERY_THRESHOLD)
 *
 * MERN/Next.js analogy:
 *   Jest unit tests for a routing utility:
 *     it('routes free users to singleStep', () => {
 *       expect(classify('protein tips', 'FREE').route).toBe('SINGLE_STEP')
 *     })
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   Test the classification logic independently so you can swap the
 *   thresholds or add new tiers without re-running full integration tests.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AgentRouterTest {

    private static final String FREE_MODEL    = "gemini-2.0-flash";
    private static final String PREMIUM_MODEL = "gemini-1.5-pro";

    /**
     * ──────────────────────────────────────────────────────────────
     * Test Matrix (Routing Logic — Binary Chart)
     * ┌───────────────┬─────────────────────┬─────────────────────┐
     * │               │ Query: Short (<80)  │ Query: Long (≥80)   │
     * ├───────────────┼─────────────────────┼─────────────────────┤
     * │ Tier: FREE    │ SINGLE_STEP,        │ SINGLE_STEP,        │
     * │               │ FREE_MODEL          │ FREE_MODEL          │
     * │ Tier: PREMIUM │ SINGLE_STEP,        │ FULL_PIPELINE,      │
     * │               │ PREMIUM_MODEL       │ PREMIUM_MODEL       │
     * └───────────────┴─────────────────────┴─────────────────────┘
     *
     * Each test covers a cell in this matrix (plus null/boundary cases).
     *
     * MERN/Next.js analogy: Jest describe.each for tier × query length.
     */

    // AgentPlatform is null — classify() never touches it.
    // Only route() (integration entry point) requires a live platform.
    private AgentRouter router;

    @BeforeEach
    void setUp() {
        router = new AgentRouter(null, FREE_MODEL, PREMIUM_MODEL);
    }

    // ── Short topic builders ───────────────────────────────────────────────

    /** Returns a topic just below the threshold — 79 chars. */
    private static String shortTopic() {
        return "a".repeat(AgentRouter.SHORT_QUERY_THRESHOLD - 1);
    }

    /** Returns a topic exactly at the threshold — 80 chars. */
    private static String longTopic() {
        return "a".repeat(AgentRouter.SHORT_QUERY_THRESHOLD);
    }

    // ── FREE tier — always SINGLE_STEP regardless of length ───────────────

    @Test
    void freeUser_shortQuery_singleStep() {
        RouteDecision decision = router.classify("protein tips", UserTier.FREE);
        assertEquals(Route.SINGLE_STEP, decision.route(),
                "FREE tier short query must always route to SINGLE_STEP");
    }

    @Test
    void freeUser_longQuery_singleStep() {
        RouteDecision decision = router.classify(longTopic(), UserTier.FREE);
        assertEquals(Route.SINGLE_STEP, decision.route(),
                "FREE tier must always use SINGLE_STEP even for long queries (cost control)");
    }

    @Test
    void freeUser_usesFreeTierModel() {
        RouteDecision decision = router.classify("omega-3 benefits", UserTier.FREE);
        assertEquals(FREE_MODEL, decision.model(),
                "FREE tier must use the configured free model name");
    }

    // ── PREMIUM tier — short query → SINGLE_STEP ──────────────────────────

    @Test
    void premiumUser_shortQuery_singleStep() {
        RouteDecision decision = router.classify(shortTopic(), UserTier.PREMIUM);
        assertEquals(Route.SINGLE_STEP, decision.route(),
                "PREMIUM tier below threshold must use SINGLE_STEP");
    }

    @Test
    void premiumUser_shortQuery_usesPremiumModel() {
        RouteDecision decision = router.classify("magnesium sleep", UserTier.PREMIUM);
        assertEquals(PREMIUM_MODEL, decision.model(),
                "PREMIUM tier must use the premium model even for short queries");
    }

    // ── PREMIUM tier — long query → FULL_PIPELINE ─────────────────────────

    @Test
    void premiumUser_longQuery_fullPipeline() {
        RouteDecision decision = router.classify(longTopic(), UserTier.PREMIUM);
        assertEquals(Route.FULL_PIPELINE, decision.route(),
                "PREMIUM tier at or above threshold must use FULL_PIPELINE");
    }

    @Test
    void premiumUser_longQuery_usesPremiumModel() {
        RouteDecision decision = router.classify(longTopic(), UserTier.PREMIUM);
        assertEquals(PREMIUM_MODEL, decision.model(),
                "PREMIUM tier FULL_PIPELINE path must use the premium model");
    }

    // ── Boundary conditions ────────────────────────────────────────────────

    @Test
    void exactlyAtThreshold_premiumUser_fullPipeline() {
        // topic.length == SHORT_QUERY_THRESHOLD (80) should be FULL_PIPELINE for PREMIUM
        String topic = "a".repeat(AgentRouter.SHORT_QUERY_THRESHOLD);
        RouteDecision decision = router.classify(topic, UserTier.PREMIUM);
        assertEquals(Route.FULL_PIPELINE, decision.route(),
                "Topic exactly at threshold must be treated as LONG for PREMIUM");
    }

    @Test
    void oneBelowThreshold_premiumUser_singleStep() {
        // topic.length == SHORT_QUERY_THRESHOLD - 1 (79) should be SINGLE_STEP
        String topic = "a".repeat(AgentRouter.SHORT_QUERY_THRESHOLD - 1);
        RouteDecision decision = router.classify(topic, UserTier.PREMIUM);
        assertEquals(Route.SINGLE_STEP, decision.route(),
                "Topic one char below threshold must be SINGLE_STEP even for PREMIUM");
    }

    // ── Null / empty defensive handling ───────────────────────────────────

    @Test
    void nullTopic_doesNotThrow() {
        // Controller validates @NotBlank before reaching router, but classify()
        // should handle null gracefully rather than throwing NPE.
        assertDoesNotThrow(() -> router.classify(null, UserTier.FREE),
                "classify() must not throw NPE on null topic");
    }

    @Test
    void nullTopic_defaultsToSingleStep() {
        RouteDecision decision = router.classify(null, UserTier.PREMIUM);
        assertEquals(Route.SINGLE_STEP, decision.route(),
                "null topic should not accidentally trigger FULL_PIPELINE");
    }
}
