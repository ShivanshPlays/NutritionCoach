package com.nutritioncoach.api;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.agent.CoachAgent;
import com.nutritioncoach.agent.LoggerService;
import com.nutritioncoach.guardrail.OutputModerator;
import com.nutritioncoach.guardrail.RateLimiter;
import com.nutritioncoach.guardrail.UnsafeOutputException;
import com.nutritioncoach.memory.MemoryService;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.CriticScore;
import com.nutritioncoach.model.ResearchBrief;
import com.nutritioncoach.observability.AgentMetricsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 5: FullAdviceController — POST /api/full-advice
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This controller exposes the two-agent pipeline introduced in Phase 5:
 *
 *   POST /api/full-advice  { "topic": "..." }
 *     Step 1: ResearchAgent.gatherFacts(UserInput) → ResearchBrief
 *     Step 2: CoachAgent.coachFromResearch(ResearchBrief) → CoachAdvice
 *
 * vs the single-agent path (POST /api/coach-advice):
 *   Step 1: CoachAgent.advise(UserInput) → CoachAdvice (pre-fetches own web search)
 *
 * When to use which endpoint:
 *   /api/coach-advice  — fast, single-agent, good for routine questions
 *   /api/full-advice   — two-stage, higher quality research, slower (2 LLM calls)
 *
 * How the pipeline works in Embabel:
 *   Each AgentInvocation.create(...).invoke(...) is an independent agent run.
 *   The controller orchestrates the handoff explicitly:
 *     1. Run ResearchAgent → get ResearchBrief (a typed Java record)
 *     2. Pass ResearchBrief as the initial world state for the CoachAgent run
 *   Embabel's GOAP planner sees ResearchBrief in the blackboard and
 *   automatically picks CoachAgent.coachFromResearch (the only @Action
 *   that requires ResearchBrief and achieves CoachAdvice).
 *
 * MERN/Next.js analogy:
 *   // Mastra workflow (Node.js):
 *   const pipeline = createWorkflow({
 *     steps: [researchStep, coachStep],
 *   })
 *   pipeline.bindTrigger('onMessage', async ({ topic }) => {
 *     const brief  = await researchStep.execute({ topic })   // step 1
 *     const advice = await coachStep.execute({ brief })      // step 2
 *     return advice
 *   })
 *
 *   // Spring equivalent (this controller):
 *   ResearchBrief  brief  = AgentInvocation.create(platform, ResearchBrief.class)
 *                                            .invoke(new UserInput(topic))
 *   CoachAdvice    advice = AgentInvocation.create(platform, CoachAdvice.class)
 *                                            .invoke(brief)
 *
 *   The Spring version is more explicit (you see both steps), while Mastra
 *   hides the wiring inside createWorkflow().  Both are correct patterns.
 *
 * Why two separate invoke() calls instead of one?
 *   Explicitness: the controller clearly documents the pipeline topology.
 *   Testability: each step can be replaced with a stub independently.
 *   Flexibility: a future Phase 11 PlannerAgent can be slotted in between.
 *
 * Book ref: Chapter 4 — Agents 101
 *   "Multi-agent = divide the task into specialised sub-tasks, each handled
 *    by a dedicated agent that does one thing well."
 *
 * Book ref: Chapter 7 — Memory (Pipeline continuity)
 *   CoachAgent.coachFromResearch() still calls MemoryTool.lookupNotes, so
 *   prior coaching sessions are injected even in the pipeline path.
 *
 * Endpoint:
 *   POST /api/full-advice
 *   Body:     { "topic": "..." }          — @NotBlank validated
 *   Response: CoachAdvice JSON            — { summary, actionItems[], weeklyGoal, disclaimer }
 *   Errors:   400 Bad Request             — blank topic
 * ═══════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api")
public class FullAdviceController {

    private static final Logger log = LoggerFactory.getLogger(FullAdviceController.class);

    // CriticAgent score threshold: if score < this, retry the coaching step.
    // Book ref: Chapter 28 — Writing LLM Evals (threshold-based retry)
    private static final int CRITIC_THRESHOLD = 40;

    // Embabel runtime bean — same as in CoachController and ResearchController.
    // MERN analogy: const mastra = new Mastra({ agents: [...] })
    private final AgentPlatform agentPlatform;

    // Phase 6: guardrail components
    private final OutputModerator outputModerator;
    private final RateLimiter rateLimiter;
    private final boolean guardrailEnabled;

    // Phase 8: async logger triggered after each pipeline completion.
    // MERN analogy: fire-and-forget background job after res.json(advice).
    private final LoggerService loggerService;
    private final MemoryService memoryService;

    // Phase 9: agent-level observability.
    private final AgentMetricsService agentMetrics;

    public FullAdviceController(AgentPlatform agentPlatform,
                                OutputModerator outputModerator,
                                RateLimiter rateLimiter,
                                @Value("${app.guardrail.enabled:false}") boolean guardrailEnabled,
                                LoggerService loggerService,
                                MemoryService memoryService,
                                AgentMetricsService agentMetrics) {
        this.agentPlatform = agentPlatform;
        this.outputModerator = outputModerator;
        this.rateLimiter = rateLimiter;
        this.guardrailEnabled = guardrailEnabled;
        this.loggerService = loggerService;
        this.memoryService = memoryService;
        this.agentMetrics = agentMetrics;
    }

    // -- POST /api/full-advice -----------------------------------------------

    record FullAdviceRequest(@NotBlank String topic) {}

    /**
     * Run the two-agent pipeline: ResearchAgent → CoachAgent.
     *
     * Step 1 invoke: UserInput → ResearchAgent.gatherFacts → ResearchBrief
     * Step 2 invoke: ResearchBrief → CoachAgent.coachFromResearch → CoachAdvice
     *
     * @param req validated request body containing the nutrition topic
     * @return structured CoachAdvice JSON produced by the full pipeline
     */
    @PostMapping("/full-advice")
    public CoachAdvice fullAdvice(@RequestBody @Valid FullAdviceRequest req,
                                  @RequestHeader(value = "X-User-Id", defaultValue = CoachAgent.DEFAULT_USER_ID) String userId) {

        // Phase 6: rate limit check before any LLM work.
        if (guardrailEnabled) {
            rateLimiter.check(userId);
        }

        // ── Step 1: Research phase ─────────────────────────────────────────
        // Phase 9: timed() wraps each AgentInvocation with durationMs + MDC.
        ResearchBrief brief = agentMetrics.timed(
                "ResearchAgent", "gatherFacts", userId, "research-system.st",
                () -> AgentInvocation
                        .create(agentPlatform, ResearchBrief.class)
                        .invoke(new UserInput(req.topic(), Instant.now())));

        // ── Step 2: Coaching phase with CriticAgent retry loop ────────────
        // Phase 6 adds: run CriticAgent after coaching; if score < threshold,
        // retry the coaching step once.
        //
        // Book ref: Chapter 28 — Writing LLM Evals
        //   "Set a threshold and use the CriticScore to gate, retry, or escalate."
        //
        // MERN analogy:
        //   const advice = await coachAgent.generate(brief)
        //   const score  = await criticAgent.generate(advice)
        //   if (!score.safe || score.score < THRESHOLD) {
        //     const retried = await coachAgent.generate(brief)  // retry once
        //   }
        CoachAdvice advice = runCoachStep(brief);

        if (guardrailEnabled) {
            CriticScore criticScore = AgentInvocation
                    .create(agentPlatform, CriticScore.class)
                    .invoke(advice);

            log.info("CriticAgent score={} safe={} feedback='{}'",
                    criticScore.score(), criticScore.safe(), criticScore.feedback());

            if (!criticScore.safe()) {
                // Unsafe content detected by the LLM judge — throw immediately.
                // OutputModerator is a keyword check; CriticAgent is the semantic check.
                throw new UnsafeOutputException(
                        "CriticAgent flagged unsafe content: " + criticScore.feedback());
            }

            if (criticScore.score() < CRITIC_THRESHOLD) {
                // Quality below threshold — retry the coaching step once.
                log.warn("CriticAgent score {} below threshold {}; retrying coaching step",
                        criticScore.score(), CRITIC_THRESHOLD);
                advice = runCoachStep(brief);
            }

            // Final output moderation (keyword-level) as last line of defence.
            outputModerator.check(advice);
        }

        // Phase 8: fire LoggerAgent in background after pipeline completes.
        // MERN analogy: setImmediate(() => loggerService.run(userId, messages))
        loggerService.runAsync(userId, memoryService.getRecentMessages(userId, 8));

        return advice;
    }

    /**
     * Run CoachAgent.coachFromResearch against the given brief.
     * Extracted to avoid duplicating the AgentInvocation call for retry.
     *
     * MERN analogy: const runCoachStep = (brief) => coachAgent.generate(brief)
     */
    private CoachAdvice runCoachStep(ResearchBrief brief) {
        return AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(brief);
    }
}
