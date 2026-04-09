package com.nutritioncoach.api;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.nutritioncoach.model.CoachAdvice;
import com.nutritioncoach.model.ResearchBrief;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // Embabel runtime bean — same as in CoachController and ResearchController.
    // MERN analogy: const mastra = new Mastra({ agents: [...] })
    private final AgentPlatform agentPlatform;

    public FullAdviceController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
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
    public CoachAdvice fullAdvice(@RequestBody @Valid FullAdviceRequest req) {

        // ── Step 1: Research phase ─────────────────────────────────────────
        // Embabel runs ResearchAgent.gatherFacts() because it is the only
        // @AchievesGoal that produces ResearchBrief from UserInput.
        //
        // MERN analogy: const brief = await researchAgent.generate(topic)
        ResearchBrief brief = AgentInvocation
                .create(agentPlatform, ResearchBrief.class)
                .invoke(new UserInput(req.topic(), Instant.now()));

        // ── Step 2: Coaching phase ─────────────────────────────────────────
        // Pass the ResearchBrief as the initial world state.  Embabel's GOAP
        // planner sees ResearchBrief=TRUE, CoachAdvice=FALSE and selects
        // CoachAgent.coachFromResearch automatically.
        //
        // MERN analogy: const advice = await coachAgent.generate(brief)
        return AgentInvocation
                .create(agentPlatform, CoachAdvice.class)
                .invoke(brief);
    }
}
