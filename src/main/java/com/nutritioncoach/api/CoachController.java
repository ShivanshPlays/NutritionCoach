package com.nutritioncoach.api;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.CoachAdvice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public CoachController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
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
    public CoachAdvice coachAdvice(@RequestBody @Valid CoachRequest req) {
        // AgentInvocation.create auto-selects CoachAgent because it is the
        // only registered agent whose @AchievesGoal produces CoachAdvice.
        // MERN analogy: mastra.getAgent('coachAgent').generate(req.topic())
        // this is an example of GOAP (Goal-Oriented Action Planning) in action.
        return AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(new UserInput(req.topic(), Instant.now()));
    }
}
