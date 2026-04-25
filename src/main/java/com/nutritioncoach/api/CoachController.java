package com.nutritioncoach.api;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.agent.CoachAgent;
import com.nutritioncoach.agent.LoggerService;
import com.nutritioncoach.guardrail.OutputModerator;
import com.nutritioncoach.guardrail.RateLimiter;
import com.nutritioncoach.memory.MemoryService;
import com.nutritioncoach.model.CoachAdvice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 3: CoachController — POST /api/coach-advice
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * What this controller does:
 *   Accepts a nutrition topic from the client and delegates to Embabel's
 *   AgentPlatform, which auto-selects CoachAgent (the only agent whose
 *   @AchievesGoal returns CoachAdvice).  CoachAgent pre-fetches tool data,
 *   then produces a structured CoachAdvice via a single LLM call.
 *
 * MERN/Next.js analogy:
 *   // Next.js App Router:
 *   export async function POST(req: Request) {
 *     const { topic } = await req.json()
 *     const advice = await coachAgent.generate(topic)
 *     return Response.json(advice)
 *   }
 *
 *   // Spring (Java):
 *   @PostMapping("/coach-advice")
 *   public CoachAdvice coachAdvice(@RequestBody @Valid CoachRequest req) {
 *     return AgentInvocation.create(agentPlatform, CoachAdvice.class)
 *                           .invoke(new UserInput(req.topic()));
 *   }
 *
 * AgentInvocation.create(platform, CoachAdvice.class):
 *   "Find the agent whose @AchievesGoal produces CoachAdvice and run it."
 *   This is "focused invocation" — the controller only knows the desired
 *   output type, not the agent class name.  Embabel's GOAP planner selects
 *   CoachAgent automatically.
 *   MERN analogy: mastra.getAgent({ outputSchema: coachAdviceSchema }).generate()
 *
 * Validation:
 *   @Valid + @NotBlank on the record field triggers Spring's Bean Validation
 *   before the controller method body runs.  A blank topic returns 400.
 *   MERN analogy: Zod z.string().min(1) on the request body schema.
 *
 * Book ref: Chapter 6 — Tool Calling
 *   The controller is intentionally thin — it knows nothing about which tools
 *   CoachAgent uses.  Tools are a CoachAgent concern, not a controller concern.
 *   MERN analogy: a Next.js route doesn't import Mastra tools directly;
 *   it calls the agent and the agent decides which tools to use.
 *
 * Endpoint:
 *   POST /api/coach-advice
 *   Body:     { "topic": "..." }          — @NotBlank validated
 *   Response: CoachAdvice JSON            — { summary, actionItems[], weeklyGoal, disclaimer }
 *   Errors:   400 Bad Request              — blank topic
 * ═══════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api")
public class CoachController {

    // MERN analogy: same as injecting the Mastra instance in a Next.js route.
    // AgentPlatform is the Embabel runtime bean, spring-wired here via ctor DI.
    private final AgentPlatform agentPlatform;

    // Phase 6: guardrail components — output moderation + rate limiting.
    // MERN analogy: injecting middleware services into the route handler.
    private final OutputModerator outputModerator;
    private final RateLimiter rateLimiter;
    private final boolean guardrailEnabled;

    // Phase 8: LoggerService fires asynchronously after each advice response.
    // MERN analogy: a background job triggered after res.json(advice).
    private final LoggerService loggerService;
    private final MemoryService memoryService;

    public CoachController(AgentPlatform agentPlatform,
                           OutputModerator outputModerator,
                           RateLimiter rateLimiter,
                           @Value("${app.guardrail.enabled:false}") boolean guardrailEnabled,
                           LoggerService loggerService,
                           MemoryService memoryService) {
        this.agentPlatform = agentPlatform;
        this.outputModerator = outputModerator;
        this.rateLimiter = rateLimiter;
        this.guardrailEnabled = guardrailEnabled;
        this.loggerService = loggerService;
        this.memoryService = memoryService;
    }

    // -- POST /api/coach-advice ---------------------------------------------

    /**
     * Request body DTO validated via Bean Validation.
     *
     * MERN analogy: a Zod-validated request type in a Next.js API route.
     *   type CoachRequest = { topic: string }  // min length enforced at runtime
     */
    record CoachRequest(@NotBlank String topic) {}

    /**
     * Generate personalised coaching advice for the given nutrition topic.
     *
     * Delegates to CoachAgent via AgentPlatform. CoachAgent will:
     *   1. Call WebSearchTool.searchWeb(topic)        — supplementary facts
     *   2. Call NutritionCalcTool.calculateNutrition  — macro data
     *   3. Call MemoryTool.lookupNotes                — user history
     *   4. Build an enriched prompt and call the LLM  — CoachAdvice output
     *   5. Call MemoryTool.storeMemory                — persist the session
     *
     * @param req validated request body containing the nutrition topic
     * @return structured CoachAdvice JSON
     */
    @PostMapping("/coach-advice")
    public CoachAdvice coachAdvice(@RequestBody @Valid CoachRequest req,
                                   @RequestHeader(value = "X-User-Id", defaultValue = CoachAgent.DEFAULT_USER_ID) String userId) {
        // Phase 6: enforce rate limit before any LLM work is done.
        // MERN analogy: rateLimiter.check(userId) in Express middleware.
        if (guardrailEnabled) {
            rateLimiter.check(userId);
        }

        // AgentInvocation.create auto-selects CoachAgent because it is the
        // only registered agent whose @AchievesGoal produces CoachAdvice.
        // MERN analogy: mastra.getAgent('coachAgent').generate(req.topic())
        CoachAdvice advice = AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(new UserInput(req.topic(), Instant.now()));

        // Phase 6: keyword-level output safety check.
        // Throws UnsafeOutputException → GuardrailExceptionHandler → HTTP 422.
        if (guardrailEnabled) {
            outputModerator.check(advice);
        }

        // Phase 8: trigger LoggerAgent asynchronously after the response is built.
        // @Async means this returns immediately; the logging runs in a background thread.
        // The user sees the advice response without waiting for logging to complete.
        // MERN analogy: setImmediate(() => loggerService.run(userId, messages))
        loggerService.runAsync(userId, memoryService.getRecentMessages(userId, 8));

        return advice;
    }
}
