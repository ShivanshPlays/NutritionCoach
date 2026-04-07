package com.nutritioncoach.api;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
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
 * Phase 2: ResearchController — delegates to Embabel ResearchAgent
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * What changed from Phase 1:
 *   Phase 1 — Controller called ChatClient directly; prompt logic lived here.
 *   Phase 2 — Controller is a thin HTTP adapter; all LLM logic is in
 *              ResearchAgent.  The controller just bridges HTTP↔agent.
 *
 * MERN/Next.js analogy:
 *   This is your Next.js App Router route handler or Express route:
 *
 *     // Next.js (Node.js):
 *     export async function POST(req: Request) {
 *       const { topic } = await req.json()
 *       const result = await researchAgent.generate(topic)
 *       return Response.json(result)
 *     }
 *
 *     // Spring (Java):
 *     @PostMapping("/research-summary")
 *     public ResearchBrief researchSummary(@RequestBody @Valid ResearchRequest req) {
 *         return AgentInvocation.create(platform, ResearchBrief.class)
 *                               .invoke(new UserInput(req.topic()));
 *     }
 *
 * AgentPlatform (injected via constructor):
 *   The Embabel runtime that manages all registered @Agent beans,
 *   runs their GOAP planner, and executes @Action chains.
 *   MERN analogy: the Mastra instance (new Mastra({ agents: [...] })).
 *
 * AgentInvocation.create(platform, ResearchBrief.class):
 *   Tells Embabel: "Find whichever @Agent has an @AchievesGoal that
 *   returns ResearchBrief, and execute it."
 *   This is "focused invocation" — we know the expected output type.
 *   MERN analogy: mastra.getAgent('researchAgent').generate(input).
 *
 * UserInput(topic, Instant.now()):
 *   Embabel's built-in wrapper for raw user text.  It is the initial state
 *   placed on the "blackboard" — ResearchAgent.gatherFacts() reads it.
 *   MERN analogy: the `messages` array / `input` string you pass to an agent.
 *
 * Book ref: Chapter 4 — Agents 101
 *   "Focused invocation" = the controller explicitly requests a specific
 *   output type. Embabel finds the path (plan) to produce it automatically.
 *
 * Endpoint:
 *   POST /api/research-summary  body: { "topic": "..." }  →  ResearchBrief JSON
 */
@RestController
@RequestMapping("/api")
public class ResearchController {

    // MERN analogy: same as injecting the Mastra instance in a Next.js route.
    // Spring DI wires AgentPlatform (an Embabel @Bean) into this controller via
    // the constructor — no 'new' keyword, no manual lookup needed.
    private final AgentPlatform agentPlatform;

    public ResearchController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    // -- POST /api/research-summary -----------------------------------------

    @PostMapping("/research-summary")
    public ResearchBrief researchSummary(@RequestBody @Valid ResearchRequest request) {
        // AgentInvocation.create() uses "agent selection by goal type":
        //   1. Scans all @Agent beans registered with the platform
        //   2. Finds the one with @AchievesGoal whose return type = ResearchBrief
        //   3. That is ResearchAgent.gatherFacts() — selected automatically
        // MERN analogy: researchAgent.generate({ topic }) — the SDK picks the
        //   model/flow; you just declare the expected shape.
        //
        // Book ref: Chapter 4 — Agents 101 (focused invocation mode)
        return AgentInvocation
                .create(agentPlatform, ResearchBrief.class)
                .invoke(new UserInput(request.topic(), Instant.now()));
    }

    // -- DTO -------------------------------------------------------------------
    // MERN analogy: interface ResearchRequest { topic: string }
    record ResearchRequest(@NotBlank(message = "topic must not be blank") String topic) {}
}

