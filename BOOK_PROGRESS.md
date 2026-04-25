# NutritionCoach — Book Progress Tracker

> **Book:** *Principles of AI Engineering, 2nd Edition* (Mastra AI book)
> **Project:** NutritionCoach — Spring Boot + Spring AI + Embabel + Gemini

This file is the source of truth for mapping every book chapter to its Java implementation.
It is updated after every task that maps to a chapter.

---

## Status key

| Symbol | Meaning |
|---|---|
| ✅ | Fully implemented |
| 🔄 | Partially implemented / in progress |
| 📋 | Planned (phase identified in plan.md) |
| ⏳ | Deferred to a later phase |
| ❌ | Not planned / out of scope |

---

## Chapter Map

### Part 1 — Foundations

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 1 | Introduction to AI Engineering | What LLM-powered apps look like; the shift from request/response to agentic flows | Vercel AI SDK quickstart; Mastra "hello world" | ✅ | [plan.md](plan.md) | Project framed, stack chosen, build order mirrors book sequence |
| 2 | Choosing a Provider & Model | Selecting an LLM provider; understanding model trade-offs (cost vs capability) | `@ai-sdk/google`, `@ai-sdk/openai` provider constructors | ✅ | [pom.xml](pom.xml), [application.yml](src/main/resources/application.yml) | Used Gemini 2.0 Flash via Google AI Studio's OpenAI-compatible endpoint; model is config-driven so it can be swapped |
| 3 | Writing Great Prompts | System prompts, few-shot examples, formatting instructions, seed crystals | `system` field in Vercel AI SDK `generateText()`; Mastra `instructions` on an Agent | ✅ | [research-system.st](src/main/resources/prompts/research-system.st), [coach-system.st](src/main/resources/prompts/coach-system.st), [ChatController.java](src/main/java/com/nutritioncoach/api/ChatController.java), [ResearchController.java](src/main/java/com/nutritioncoach/api/ResearchController.java) | System prompts versioned as classpath `.st` template files; loaded at startup; include explicit JSON schema instructions |

---

### Part 2 — Building with LLMs

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 4 | Agents 101 | Agent = LLM + memory + tools + goal; agent lifecycle; autonomy | Mastra `Agent` class with `model`, `instructions`, `tools`, `memory` | ✅ | [ResearchAgent.java](src/main/java/com/nutritioncoach/agent/ResearchAgent.java), [CoachAgent.java](src/main/java/com/nutritioncoach/agent/CoachAgent.java), [ResearchController.java](src/main/java/com/nutritioncoach/api/ResearchController.java), [FullAdviceController.java](src/main/java/com/nutritioncoach/api/FullAdviceController.java), [CoachAgentPipelineTest.java](src/test/java/com/nutritioncoach/agent/CoachAgentPipelineTest.java) | Phase 5: `CoachAgent.coachFromResearch(ResearchBrief, Ai)` added as second `@AchievesGoal @Action`; Embabel GOAP selects it automatically when ResearchBrief is in blackboard; `FullAdviceController` chains two `AgentInvocation` calls (ResearchBrief → CoachAdvice) |
| 5 | Structured Output | Making the model return typed data, not free text; schema-driven generation | Vercel AI SDK `generateObject()` + Zod schema; Mastra output schema | ✅ | [ResearchBrief.java](src/main/java/com/nutritioncoach/model/ResearchBrief.java), [ResearchController.java](src/main/java/com/nutritioncoach/api/ResearchController.java) | `ChatClient.call().entity(ResearchBrief.class)` — Spring AI infers the JSON schema from the Java record and instructs the model |
| 6 | Tool Calling | Giving agents callable functions; tool design principles | Mastra `createTool()`; Vercel AI SDK `tools` param | ✅ | [WebSearchTool.java](src/main/java/com/nutritioncoach/tool/WebSearchTool.java), [MemoryTool.java](src/main/java/com/nutritioncoach/tool/MemoryTool.java), [NutritionCalcTool.java](src/main/java/com/nutritioncoach/tool/NutritionCalcTool.java), [CoachAgent.java](src/main/java/com/nutritioncoach/agent/CoachAgent.java), [CoachController.java](src/main/java/com/nutritioncoach/api/CoachController.java) | Pre-fetch pattern: Java code calls tools before LLM; tools are `@Component` beans injected via Spring DI; `WebSearchTool` is a Phase 3 stub; `MemoryTool` is in-memory (Phase 4 adds JPA persistence) |
| 7 | Memory | Short-term (conversation window) vs long-term (persistent notes/profile) | Mastra `Memory` with `LibSQLStore`; Vercel AI SDK `useChat` + custom storage | ✅ | [MemoryService.java](src/main/java/com/nutritioncoach/memory/MemoryService.java), [JpaMemoryService.java](src/main/java/com/nutritioncoach/memory/JpaMemoryService.java), [ConversationMessage.java](src/main/java/com/nutritioncoach/memory/ConversationMessage.java), [AgentNote.java](src/main/java/com/nutritioncoach/memory/AgentNote.java), [UserProfile.java](src/main/java/com/nutritioncoach/memory/UserProfile.java), [MemoryTool.java](src/main/java/com/nutritioncoach/tool/MemoryTool.java), [ChatController.java](src/main/java/com/nutritioncoach/api/ChatController.java), [InMemoryMemoryService.java](src/test/java/com/nutritioncoach/memory/InMemoryMemoryService.java), [LoggerAgent.java](src/main/java/com/nutritioncoach/agent/LoggerAgent.java), [LoggerService.java](src/main/java/com/nutritioncoach/agent/LoggerService.java) | Phase 5: `MemoryService` extracted as interface; `JpaMemoryService` is production impl; Phase 8: `LoggerAgent` extracts facts/preferences from conversation; `LoggerService` merges results into `UserProfile` and `AgentNote` asynchronously |

---

### Part 3 — Advanced Patterns

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 8 | Dynamic Agents | Changing instructions, tools, or model at runtime based on context/user tier | Mastra conditional tool lists; runtime `instructions` override | ✅ | [AgentRouter.java](src/main/java/com/nutritioncoach/agent/AgentRouter.java), [RouteAdviceController.java](src/main/java/com/nutritioncoach/api/RouteAdviceController.java), [UserTier.java](src/main/java/com/nutritioncoach/model/UserTier.java), [RouteAdviceResponse.java](src/main/java/com/nutritioncoach/model/RouteAdviceResponse.java), [AgentRouterTest.java](src/test/java/com/nutritioncoach/agent/AgentRouterTest.java), [LoggerAgent.java](src/main/java/com/nutritioncoach/agent/LoggerAgent.java), [LoggerService.java](src/main/java/com/nutritioncoach/agent/LoggerService.java), [AsyncConfig.java](src/main/java/com/nutritioncoach/config/AsyncConfig.java), [LoggerAgentTest.java](src/test/java/com/nutritioncoach/agent/LoggerAgentTest.java) | Phase 7: routing by tier/complexity; Phase 8: `LoggerAgent` runs asynchronously after each advice call (`@Async`) — dedicated background agent for memory lifecycle; `AsyncConfig` enables Spring async execution |
| 9 | Middleware & Guardrails | Input sanitisation, prompt injection detection, output moderation | Express middleware (`app.use()`); Mastra middleware hooks | ✅ | [InputSanitiser.java](src/main/java/com/nutritioncoach/guardrail/InputSanitiser.java), [InputGuardrailFilter.java](src/main/java/com/nutritioncoach/guardrail/InputGuardrailFilter.java), [OutputModerator.java](src/main/java/com/nutritioncoach/guardrail/OutputModerator.java), [RateLimiter.java](src/main/java/com/nutritioncoach/guardrail/RateLimiter.java), [ApiKeyInterceptor.java](src/main/java/com/nutritioncoach/guardrail/ApiKeyInterceptor.java), [CriticAgent.java](src/main/java/com/nutritioncoach/agent/CriticAgent.java), [GuardrailExceptionHandler.java](src/main/java/com/nutritioncoach/api/GuardrailExceptionHandler.java) | Phase 6: `OncePerRequestFilter` for injection detection; `HandlerInterceptor` for API-key auth; `OutputModerator` for keyword safety; `CriticAgent` for LLM-as-judge semantic gating; `RateLimiter` sliding-window per userId |
| 10 | Third-Party Tools & Integrations | Wrapping external APIs as agent-callable tools | Mastra community tools (`@mastra/tools`) | � | [WebSearchTool.java](src/main/java/com/nutritioncoach/tool/WebSearchTool.java) | `WebSearchTool` stub wraps a simulated search API; Phase 10 RAG will replace backing with real search (Brave/Tavily) or pgvector retrieval |
| 11 | Model Context Protocol (MCP) | Standard interface for connecting agents to tool servers | MCP SDK for Node.js; `@mastra/mcp` | 📋 | *(Phase 14)* | Embabel can expose tools as an MCP server over SSE |

---

### Part 4 — Production Patterns

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 12 | Workflows — Basics | Deterministic orchestration: chaining agent steps predictably | Mastra `workflow()` with `step()` chain | ✅ | [FullAdviceController.java](src/main/java/com/nutritioncoach/api/FullAdviceController.java), [CoachAgent.java](src/main/java/com/nutritioncoach/agent/CoachAgent.java), [CoachAgentPipelineTest.java](src/test/java/com/nutritioncoach/agent/CoachAgentPipelineTest.java) | Phase 5: explicit 2-step Java pipeline — `AgentInvocation` #1 produces `ResearchBrief`, #2 produces `CoachAdvice`; controller is the workflow engine (maps to a Mastra `workflow()` with two `step()` calls) |
| 13 | Workflows — Branching | Conditional paths, parallel branches, merge | Mastra `branch()`, `parallel()` | 📋 | *(Phase 9)* | PlannerAgent will drive branching |
| 14 | Workflows — Suspend & Resume | Long-running workflows that pause for human input or async events | Mastra `suspend()` / `resume()` | ⏳ | — | Deferred; requires durable execution support |
| 15 | Streaming | Token-by-token SSE output from the model to the client | Vercel AI SDK `streamText()`; `useChat` hook | 📋 | *(Phase 12)* | `ChatClient.stream()` → SSE endpoint |
| 16 | Observability | Traces, step-level logs, timing, prompt version tracking | Mastra `telemetry` config; OpenTelemetry | ✅ | [AgentMetricsService.java](src/main/java/com/nutritioncoach/observability/AgentMetricsService.java), [CoachAgent.java](src/main/java/com/nutritioncoach/agent/CoachAgent.java), [CoachController.java](src/main/java/com/nutritioncoach/api/CoachController.java), [FullAdviceController.java](src/main/java/com/nutritioncoach/api/FullAdviceController.java), [RouteAdviceController.java](src/main/java/com/nutritioncoach/api/RouteAdviceController.java), [AgentMetricsServiceTest.java](src/test/java/com/nutritioncoach/observability/AgentMetricsServiceTest.java) | Phase 9: `AgentMetricsService` wraps every agent action and tool call with Micrometer timers + MDC + structured log events; `micrometer-tracing-bridge-brave` adds traceId/spanId to every log line; `/actuator/metrics` and `/actuator/loggers` exposed; `hashInput()` ensures no raw PII in logs |

---

### Part 5 — RAG & Knowledge

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 17 | RAG — Chunking & Ingestion | Breaking documents into searchable pieces | LangChain.js `RecursiveCharacterTextSplitter` | 📋 | *(Phase 10)* | `DocumentIngestionService` with configurable chunk size |
| 18 | RAG — Embedding & Indexing | Converting text to vectors; storing in a vector DB | `@ai-sdk/google` `embed()`; Pinecone / pgvector | 📋 | *(Phase 10)* | Spring AI `EmbeddingClient` + `PgVectorStore` |
| 19 | RAG — Retrieval & Reranking | Top-K similarity search; reranking; query rewriting | LangChain.js retriever; Mastra `vectorQueryTool` | 📋 | *(Phase 10)* | `RetrievalTool` wraps `PgVectorStore.similaritySearch()` |
| 20 | RAG — Synthesis | Injecting retrieved context into a prompt; grounding | Mastra RAG pipeline end-to-end | 📋 | *(Phase 10)* | Context injected into `ResearchAgent` prompt at call time |

---

### Part 6 — Multi-Agent Systems

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 21 | Multi-Agent Intro | Why multiple specialised agents beat one large agent | Mastra multi-agent example | 📋 | *(Phase 11)* | Research + Coach + Logger + Critic + Planner architecture |
| 22 | Supervisor Pattern | A planner/orchestrator agent dispatches to worker agents | Mastra `Agent` calling other agents as tools | 📋 | *(Phase 11)* | `PlannerAgent` orchestrates all specialists |
| 23 | Agent Networks & Delegation | Agents calling each other; result passing | Mastra agent-to-agent calling | 📋 | *(Phase 11)* | Implemented via Embabel goal/plan execution |
| 24 | Workflows as Tools | Wrapping a workflow as a single callable tool for another agent | Mastra workflow exposed as a tool | 📋 | *(Phase 11)* | ResearchAgent → CoachAgent pipeline exposed as a single tool |
| 25 | Combining Patterns | Real-world mix of supervisor + workflow + tools + RAG | Mastra complete project | 📋 | *(Phase 11)* | Full multi-agent coaching flow |
| 26 | Agent Communication Protocols | Standardised message passing between agents | MCP, A2A protocol | ⏳ | — | Handled via Embabel's internal bus + MCP in Phase 14 |

---

### Part 7 — Quality & Evals

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 27 | Evaluations — Overview | Why LLM output needs tests; non-determinism | Mastra `evalSet`; Promptfoo | 📋 | *(Phase 13)* | `EvalService` with JUnit-based assertion fixtures |
| 28 | Writing LLM Evals | Schema checks, grounding scores, safety filters, tool-coverage asserts | Mastra LLM-as-judge evals | � | [CriticAgent.java](src/main/java/com/nutritioncoach/agent/CriticAgent.java), [CriticScore.java](src/main/java/com/nutritioncoach/model/CriticScore.java), [CriticAgentTest.java](src/test/java/com/nutritioncoach/agent/CriticAgentTest.java) | Phase 6: `CriticAgent` implements LLM-as-judge pattern inline (score 0-100 + safe flag + retry). Full offline eval suite (Phase 13) remains 📋 |
| 29 | CI for AI | Running evals in CI; gating merges on quality thresholds | GitHub Actions + Promptfoo CI | 📋 | *(Phase 13)* | Maven Surefire gate; CI config TBD |

---

### Part 8 — Deployment

| Ch | Title | Core concept | MERN/JS equivalent | Status | Implementing files | Notes |
|---|---|---|---|---|---|---|
| 30 | Local Development | Dev playground; agent endpoint; tool test endpoint; trace inspection | Mastra dev server (`mastra dev`); Next.js `next dev` | 🔄 | [.env.example](.env.example), [application.yml](src/main/resources/application.yml) | H2 console + Actuator endpoints available locally; agent playground in Phase 13 |
| 31 | Deployment | Docker packaging; environment config; long-running agent workloads | Vercel deploy; Dockerfile; Railway | 📋 | *(Phase 15)* | Multi-stage Dockerfile + docker-compose with PostgreSQL + pgvector |

---

## Implementation Timeline

```
Phase 0  → Ch 1, 2       (provider/model, project bootstrap)         ✅
Phase 1  → Ch 3, 5       (prompts, structured output)                 ✅
Phase 2  → Ch 4          (first Embabel @Agent)                       📋
Phase 3  → Ch 6, 10      (tool calling, third-party tools)            📋
Phase 4  → Ch 7          (memory layer)                               📋
Phase 5  → Ch 12         (first workflow: Research → Coach)           📋
Phase 6  → Ch 9          (guardrails & middleware)                    ✅
Phase 7  → Ch 8          (dynamic agents / model routing)             ✅
Phase 8  → LoggerAgent   (memory lifecycle agent)                     ✅
Phase 9  → Ch 13, 16     (branching workflows, observability)         📋
Phase 10 → Ch 17-20      (RAG)                                        📋
Phase 11 → Ch 21-26      (multi-agent supervisor)                     📋
Phase 12 → Ch 15         (streaming)                                  📋
Phase 13 → Ch 27-29      (evals + CI)                                 📋
Phase 14 → Ch 11         (MCP server)                                 📋
Phase 15 → Ch 31         (Docker + deployment)                        📋
```

---

*Last updated: Phase 8 complete.*
