package com.nutritioncoach.api;

import com.nutritioncoach.agent.AgentRouter;
import com.nutritioncoach.agent.CoachAgent;
import com.nutritioncoach.guardrail.OutputModerator;
import com.nutritioncoach.guardrail.RateLimiter;
import com.nutritioncoach.model.RouteAdviceResponse;
import com.nutritioncoach.model.UserTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 7: RouteAdviceController — POST /api/route-advice
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Thin controller that delegates all routing logic to AgentRouter.
 * The route is determined by two inputs from the HTTP request:
 *   1. X-User-Tier header → UserTier (FREE / PREMIUM, default FREE)
 *   2. topic length       → short vs long query classification in AgentRouter
 *
 * Why a new endpoint instead of modifying /api/coach-advice?
 *   Separation of concerns: the existing endpoints represent specific,
 *   predictable paths (single-step, full pipeline).  The router endpoint
 *   introduces a new abstraction — adaptive routing — which should not
 *   silently change existing endpoint behaviour.
 *   This also mirrors the Mastra model: you add a new workflow trigger
 *   rather than changing an existing one's behaviour.
 *
 * How routing works (AgentRouter):
 *   ┌─────────┬────────────────┬──────────────────┬──────────────────────┐
 *   │  Tier   │ Query length   │ Route selected   │ Model used           │
 *   ├─────────┼────────────────┼──────────────────┼──────────────────────┤
 *   │  FREE   │ any            │ SINGLE_STEP      │ gemini-2.0-flash     │
 *   │ PREMIUM │ < 80 chars     │ SINGLE_STEP      │ gemini-1.5-pro       │
 *   │ PREMIUM │ ≥ 80 chars     │ FULL_PIPELINE    │ gemini-1.5-pro       │
 *   └─────────┴────────────────┴──────────────────┴──────────────────────┘
 *
 * Response shape (RouteAdviceResponse):
 *   {
 *     "advice":  { summary, actionItems[], weeklyGoal, disclaimer },
 *     "tier":    "FREE" | "PREMIUM",
 *     "route":   "SINGLE_STEP" | "FULL_PIPELINE",
 *     "model":   "gemini-2.0-flash" | "gemini-1.5-pro"
 *   }
 *
 * MERN/Next.js analogy:
 *   // Next.js App Router:
 *   export async function POST(req: Request) {
 *     const { topic } = await req.json()
 *     const tier  = req.headers.get('X-User-Tier') ?? 'FREE'
 *     const result = await agentRouter.route(topic, tier)
 *     return Response.json(result)
 *   }
 *
 * Book ref: Chapter 8 — Dynamic Agents
 *   "Controllers that expose dynamic agents should accept tier/context
 *    signals from the client so the routing decision is transparent."
 *
 * Endpoint:
 *   POST /api/route-advice
 *   Headers: X-User-Id  (optional, default: "default")
 *            X-User-Tier (optional: FREE | PREMIUM, default: FREE)
 *            X-Api-Key  (required when guardrails enabled)
 *   Body:     { "topic": "..." }
 *   Response: RouteAdviceResponse JSON
 *   Errors:   400 blank topic | 401 missing key | 422 unsafe output | 429 rate limited
 * ═══════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api")
public class RouteAdviceController {

    // AgentRouter encapsulates all routing + agent invocation logic.
    // MERN analogy: a service injected into a Next.js route handler.
    private final AgentRouter agentRouter;

    // Phase 6 guardrail components wired into this controller too.
    // MERN analogy: Express middleware functions called inside the handler.
    private final RateLimiter rateLimiter;
    private final OutputModerator outputModerator;
    private final boolean guardrailEnabled;

    public RouteAdviceController(AgentRouter agentRouter,
                                 RateLimiter rateLimiter,
                                 OutputModerator outputModerator,
                                 @Value("${app.guardrail.enabled:false}") boolean guardrailEnabled) {
        this.agentRouter = agentRouter;
        this.rateLimiter = rateLimiter;
        this.outputModerator = outputModerator;
        this.guardrailEnabled = guardrailEnabled;
    }

    // ── Request / Response types ───────────────────────────────────────────

    /**
     * Request body DTO — same shape as CoachRequest.
     * MERN analogy: Zod z.object({ topic: z.string().min(1) })
     */
    record RouteAdviceRequest(@NotBlank String topic) {}

    // ── Handler ────────────────────────────────────────────────────────────

    /**
     * Dynamically route the request based on tier and query complexity.
     *
     * @param req        validated request body
     * @param userId     identifies the user for rate limiting and memory
     * @param tierHeader raw tier string from header (invalid values default to FREE)
     * @return RouteAdviceResponse — advice + routing metadata
     */
    @PostMapping("/route-advice")
    public RouteAdviceResponse routeAdvice(
            @RequestBody @Valid RouteAdviceRequest req,
            @RequestHeader(value = "X-User-Id",   defaultValue = CoachAgent.DEFAULT_USER_ID) String userId,
            @RequestHeader(value = "X-User-Tier", defaultValue = "FREE") String tierHeader) {

        // Phase 6: rate limit check before any LLM work.
        // MERN analogy: rateLimiter.check(userId) in Express middleware.
        if (guardrailEnabled) {
            rateLimiter.check(userId);
        }

        // Parse tier header defensively — unknown values → FREE.
        // MERN analogy: ['FREE','PREMIUM'].includes(header) ? header : 'FREE'
        UserTier tier = parseTier(tierHeader);

        // Delegate routing + agent invocation to AgentRouter.
        // The controller has no routing logic — it only reads the request and
        // delegates.  This follows the thin-controller principle.
        RouteAdviceResponse response = agentRouter.route(req.topic(), tier);

        // Phase 6: keyword-level output moderation as last line of defence.
        // OutputModerator checks the nested advice field.
        if (guardrailEnabled) {
            outputModerator.check(response.advice());
        }

        return response;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Parse the X-User-Tier header value into a UserTier enum.
     * Returns FREE for any unrecognised value — fail-safe default.
     *
     * MERN analogy:
     *   const parseTier = (h: string): UserTier =>
     *     (h?.toUpperCase() === 'PREMIUM') ? 'PREMIUM' : 'FREE'
     */
    private UserTier parseTier(String tierHeader) {
        try {
            return UserTier.valueOf(tierHeader.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UserTier.FREE;
        }
    }
}
