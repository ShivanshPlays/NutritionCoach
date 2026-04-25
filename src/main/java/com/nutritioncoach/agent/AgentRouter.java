package com.nutritioncoach.agent;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.ResearchBrief;
import com.nutritioncoach.model.RouteAdviceResponse;
import com.nutritioncoach.model.UserTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

import javax.swing.Spring;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AgentRouter — dynamic routing based on user tier and query complexity
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Phase 7 core component.  Encapsulates the two routing decisions:
 *   1. WHICH PATH to execute (single-step vs full pipeline)
 *   2. WHICH MODEL to use (free model vs premium model)
 *
 * This is NOT an @Agent — it is a plain Spring @Service that orchestrates
 * agent invocations.  The distinction matters:
 *   @Agent  → something Embabel's GOAP planner can auto-select and sequence
 *   @Service → Java-controlled orchestration with explicit routing logic
 *
 * Routing rules (Binary Chart):
 * ┌───────────────┬─────────────────────┬─────────────────────┐
 * │               │ Query: Short (<80)  │ Query: Long (≥80)   │
 * ├───────────────┼─────────────────────┼─────────────────────┤
 * │ Tier: FREE    │ SINGLE_STEP,        │ SINGLE_STEP,        │
 * │               │ FREE_MODEL          │ FREE_MODEL          │
 * │ Tier: PREMIUM │ SINGLE_STEP,        │ FULL_PIPELINE,      │
 * │               │ PREMIUM_MODEL       │ PREMIUM_MODEL       │
 * └───────────────┴─────────────────────┴─────────────────────┘
 *
 * This chart is also covered by the AgentRouterTest suite.
 *
 * Why FREE → always SINGLE_STEP?
 *   The full pipeline runs two LLM calls (ResearchAgent + CoachAgent).
 *   For a FREE tier, keeping to a single LLM call is a cost-control measure.
 *   MERN analogy: rate-limiting features behind a paywall.
 *
 * Why PREMIUM + short query → SINGLE_STEP?
 *   Short queries like "protein tips" are simple enough for CoachAgent alone.
 *   Running the full research pipeline for simple questions is wasteful.
 *   MERN analogy: feature-flagging "enhanced mode" only when needed.
 *
 * Model name note:
 *   The `model` field in RouteAdviceResponse shows which Gemini model was
 *   SELECTED for this tier.  Current Embabel agents call ai.withDefaultLlm(),
 *   which uses the globally configured model.  True per-request model
 *   switching via Embabel's API will be wired in Phase 11 (Multi-Agent
 *   Supervisor), where the platform provides a richer invocation context.
 *
 *   For now, the model name is:
 *     • Logged for observability (Phase 9 will emit it in structured traces)
 *     • Returned in the response for client-side visiblity
 *     • Available as a hook for Phase 11 to pass to AgentInvocationBuilder
 *
 * MERN/Next.js analogy (Mastra dynamic routing):
 *   const routeAdvice = async (topic: string, tier: UserTier) => {
 *     const model = tier === 'PREMIUM' ? premiumModel : freeModel
 *     const isLong = topic.length >= SHORT_QUERY_THRESHOLD
 *     if (tier === 'PREMIUM' && isLong) {
 *       const brief  = await researchAgent.generate(topic)  // step 1
 *       const advice = await coachAgent.generate(brief)     // step 2
 *       return { advice, tier, route: 'FULL_PIPELINE', model }
 *     }
 *     const advice = await coachAgent.generate(topic)
 *     return { advice, tier, route: 'SINGLE_STEP', model }
 *   }
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Change the model, tools, or instructions at runtime based on context.
 *    User tier is one dimension; query complexity is another."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Service
public class AgentRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    /**
     * Topic character-count threshold that divides "simple" from "complex" queries.
     * Queries at or above this length are considered research-worthy for PREMIUM users.
     *
     * Examples below threshold (SINGLE_STEP):
     *   "protein tips"                — 12 chars, simple intent
     *   "magnesium dosage"            — 16 chars, lookup question
     *
     * Examples at or above threshold (FULL_PIPELINE, if PREMIUM):
     *   "What evidence supports intermittent fasting for fat loss and lean muscle retention?"
     *   "Compare omega-3 sources: fish oil vs algae supplement for vegans"
     *
     * Package-visible for unit testing.
     */
    static final int SHORT_QUERY_THRESHOLD = 80;

    private final AgentPlatform agentPlatform;

    // Model names from config — injected via @Value.
    // Book ref: Chapter 2 — Choosing a Provider & Model
    //   "Externalise model names; never hardcode them."
    // MERN analogy: process.env.FREE_MODEL / process.env.PREMIUM_MODEL
    private final String freeModelName;
    private final String premiumModelName;

    // Without @Value, Spring cannot inject config values directly into those constructor parameters,
    // and bean creation will fail unless you provide those Strings some other way (e.g., via a @Configuration class or manual wiring).
    public AgentRouter(
            AgentPlatform agentPlatform,
            @Value("${app.agent.model.free:gemini-2.0-flash}") String freeModelName,
            @Value("${app.agent.model.premium:gemini-1.5-pro}") String premiumModelName) {
        this.agentPlatform = agentPlatform;
        this.freeModelName = freeModelName;
        this.premiumModelName = premiumModelName;
    }

    // ── Routing decision value object ─────────────────────────────────────

    /**
     * The two possible routes through the agent graph.
     *
     * MERN analogy:
     *   type Route = 'SINGLE_STEP' | 'FULL_PIPELINE'
     */
    public enum Route {
        /** CoachAgent.advise(UserInput) — 1 LLM call, fast, for FREE tier and short queries. */
        SINGLE_STEP,
        /** ResearchAgent → CoachAgent.coachFromResearch — 2 LLM calls, higher quality. */
        FULL_PIPELINE
    }

    /**
     * Immutable value object capturing the routing decision.
     * Package-visible so tests can call classify() directly.
     *
     * MERN analogy:
     *   type RouteDecision = { route: Route; model: string }
     */
    record RouteDecision(Route route, String model) {}

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Classify the request, run the appropriate agent path, and return
     * the advice wrapped with routing metadata.
     *
     * @param topic the user's nutrition question
     * @param tier  the user's subscription tier (from X-User-Tier header)
     * @return RouteAdviceResponse containing advice + routing metadata
     */
    public RouteAdviceResponse route(String topic, UserTier tier) {
        RouteDecision decision = classify(topic, tier);

        // Log the routing decision for observability.
        // Phase 9 will emit this as a structured trace event.
        log.info("AgentRouter: topic.length={} tier={} route={} model={}",
                topic.length(), tier, decision.route(), decision.model());

        CoachAdvice advice = switch (decision.route()) {
            case SINGLE_STEP    -> runSingleStep(topic);
            case FULL_PIPELINE  -> runFullPipeline(topic);
        };

        return new RouteAdviceResponse(advice, tier.name(), decision.route().name(), decision.model());
    }

    // ── Routing classification ─────────────────────────────────────────────

    /**
     * Pure routing-logic method: maps (topic, tier) → RouteDecision.
     * No LLM calls, no I/O — package-visible and fully unit-testable.
     *
     * MERN analogy:
     *   const classify = (topic: string, tier: UserTier): RouteDecision => {
     *     const isPremium = tier === 'PREMIUM'
     *     const isLong    = topic.length >= SHORT_QUERY_THRESHOLD
     *     if (isPremium && isLong) return { route: 'FULL_PIPELINE', model: premiumModel }
     *     return { route: 'SINGLE_STEP', model: isPremium ? premiumModel : freeModel }
     *   }
     *
     * @param topic raw topic string (not null — validated by controller)
     * @param tier  the user's tier
     */
    RouteDecision classify(String topic, UserTier tier) {
        boolean isPremium = tier == UserTier.PREMIUM;
        boolean isLongQuery = topic != null && topic.strip().length() >= SHORT_QUERY_THRESHOLD;

        if (isPremium && isLongQuery) {
            // PREMIUM + complex query → full research pipeline
            return new RouteDecision(Route.FULL_PIPELINE, premiumModelName);
        }

        // All FREE users + PREMIUM users with short queries → fast single-step path
        return new RouteDecision(Route.SINGLE_STEP, isPremium ? premiumModelName : freeModelName);
    }

    // ── Agent invocation helpers ──────────────────────────────────────────

    /**
     * Single-step path: CoachAgent.advise(UserInput) → CoachAdvice.
     * One LLM call; suitable for simple questions and FREE tier.
     *
     * MERN analogy: await coachAgent.generate(topic)
     */
    private CoachAdvice runSingleStep(String topic) {
        return AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(new UserInput(topic, Instant.now()));
    }

    /**
     * Full pipeline: ResearchAgent → CoachAgent.coachFromResearch → CoachAdvice.
     * Two LLM calls; higher quality for complex PREMIUM queries.
     *
     * MERN analogy:
     *   const brief  = await researchAgent.generate(topic)
     *   const advice = await coachAgent.generate(brief)
     */
    private CoachAdvice runFullPipeline(String topic) {
        // Step 1 — ResearchAgent.gatherFacts produces ResearchBrief
        ResearchBrief brief = AgentInvocation
                .create(agentPlatform, ResearchBrief.class)
                .invoke(new UserInput(topic, Instant.now()));

        // Step 2 — CoachAgent.coachFromResearch consumes ResearchBrief
        return AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(brief);
    }
}
