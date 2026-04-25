# NutritionCoach — High-Level Plan

> **Stack:** Java 21 · Spring Boot 3.x · Spring AI · Embabel · Gemini (Google AI Studio) · PostgreSQL (H2 for early dev) · Flyway · Maven

---

## Vision

A "Research + Coach + Logger" nutrition assistant with multiple cooperative agents:

| Agent | Responsibility |
|---|---|
| **ResearchAgent** | Gathers nutritional facts, ingredient info, scientific backing |
| **CoachAgent** | Transforms research into personalised advice for the user |
| **LoggerAgent** | Persists conversation state, user preferences, meal logs |
| **CriticAgent** (later) | Reviews output for safety, quality, and grounding |
| **PlannerAgent** (later) | Decomposes complex user tasks and orchestrates the other agents |

---

## Repository Layout (target)

```
NutritionCoach/
├── plan.md                        ← this file
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/nutritioncoach/
│   │   │   ├── NutritionCoachApplication.java
│   │   │   ├── config/            ← Spring AI, Security, Embabel config
│   │   │   ├── agent/             ← @Agent classes (Embabel)
│   │   │   │   ├── ResearchAgent.java
│   │   │   │   ├── CoachAgent.java
│   │   │   │   ├── LoggerAgent.java
│   │   │   │   └── CriticAgent.java
│   │   │   ├── action/            ← @Action methods wired by Embabel
│   │   │   ├── tool/              ← Spring beans exposed as LLM tools
│   │   │   │   ├── WebSearchTool.java
│   │   │   │   ├── MemoryTool.java
│   │   │   │   └── NutritionCalcTool.java
│   │   │   ├── model/             ← Structured output records / entities
│   │   │   │   ├── ResearchBrief.java
│   │   │   │   ├── MealAnalysis.java
│   │   │   │   └── CoachAdvice.java
│   │   │   ├── memory/            ← Memory service + JPA entities
│   │   │   │   ├── ConversationMessage.java
│   │   │   │   ├── UserProfile.java
│   │   │   │   └── MemoryService.java
│   │   │   ├── workflow/          ← Orchestration / pipeline logic
│   │   │   ├── rag/               ← Document ingestion, embedding, retrieval
│   │   │   ├── guardrail/         ← Input/output safety checks
│   │   │   ├── mcp/               ← MCP server exposure (late phase)
│   │   │   └── api/               ← REST controllers
│   │   │       ├── ChatController.java
│   │   │       ├── ResearchController.java
│   │   │       └── CoachController.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── prompts/           ← Versioned prompt templates (.st / .txt)
│   │       └── db/migration/      ← Flyway SQL scripts
│   └── test/
│       └── java/com/nutritioncoach/
│           ├── agent/             ← Embabel agent unit tests
│           ├── tool/              ← Tool unit tests
│           └── eval/              ← LLM-output eval assertions
```

---

## Build Phases

### Phase 0 — Project Bootstrap ✅
*Goal: running Spring Boot app that calls Gemini and returns text.*

> **Note:** Spring AI 1.0.0 has no native Google AI Studio starter.
> We use `spring-ai-starter-model-openai` pointed at Google AI Studio's
> OpenAI-compatible endpoint (`generativelanguage.googleapis.com/v1beta/openai`).
> The `ChatClient` API is identical — just set `GEMINI_API_KEY`.

- [x] Initialise Maven project with Java 21 and Spring Boot 3.4.4
- [x] Add dependencies:
  - `spring-boot-starter-web`
  - `spring-ai-starter-model-openai` (BOM-managed; pointed at Google AI Studio)
  - `spring-boot-starter-data-jpa`
  - `com.h2database:h2` (runtime)
  - `org.flywaydb:flyway-core` (disabled until Phase 4)
  - `spring-boot-starter-validation`
  - `spring-boot-starter-actuator`
  - `lombok` (optional)
  - Embabel commented out (Phase 2)
- [x] Set Gemini API key in `application.yml` via env var `GEMINI_API_KEY`
- [x] Create `GET /api/health` → `{ "status": "ok" }`
- [x] Create `POST /api/chat` body `{ "message": "..." }` → raw Gemini text reply
- [x] `mvn test` passes — context loads, H2 console available

**Deliverable:** `BUILD SUCCESS`, one working Gemini round-trip. ✅

---

### Phase 1 — Prompting Foundation ✅
*Goal: move from raw text to structured, templated prompts.*

- [x] Add versioned prompt templates in `src/main/resources/prompts/`
  - `research-system.st` — system prompt with output schema instructions
  - `coach-system.st` — coaching system prompt with output schema instructions
- [x] Add `POST /api/research-summary` endpoint
  - Input: `{ "topic": "..." }`
  - Output: typed `ResearchBrief` JSON
  - Uses `ChatClient` with system prompt loaded from classpath template + `{topic}` param
- [x] Structured output — `ResearchBrief` record:
  ```java
  record ResearchBrief(
      String topic,
      List<String> keyFindings,
      List<String> risks,
      List<String> nextQuestions
  ) {}
  ```
  Uses `ChatClient.prompt(...).call().entity(ResearchBrief.class)`
- [x] System prompts include explicit JSON format instructions (no markdown fences)

**Deliverable:** `POST /api/research-summary` returns a typed `ResearchBrief` JSON. ✅

---

### Phase 2 — First Embabel Agent ✅
*Goal: replace ad-hoc controller logic with a proper `@Agent`.*

- [x] Add Embabel Spring Boot auto-configuration
  - `com.embabel.agent:embabel-agent-starter:0.3.4` (core platform)
  - `com.embabel.agent:embabel-agent-starter-gemini:0.3.4` (registers Gemini model beans)
  - `com.embabel.agent:embabel-agent-test:0.3.4` (FakeOperationContext for unit tests)
  - Spring AI bumped from 1.0.0 → 1.1.1 (required by Embabel 0.3.4)
- [x] Create `ResearchAgent` annotated with `@Agent`
  - Single `@Action` + `@AchievesGoal` method: `gatherFacts(UserInput, Ai) → ResearchBrief`
  - Uses `ai.withDefaultLlm().createObject(prompt, ResearchBrief.class)` for structured output
- [x] Wire `ResearchAgent` into `ResearchController`
  - Controller now injects `AgentPlatform` (Embabel runtime)
  - Uses `AgentInvocation.create(platform, ResearchBrief.class).invoke(new UserInput(topic))`
  - Agent auto-selected by goal type (no explicit agent name needed)
- [x] Write unit test for `ResearchAgent` using Embabel's test utilities (mocked LLM)
  - `ResearchAgentTest` uses `FakeOperationContext.create()` + `FakePromptRunner`
  - Verifies topic appears in outgoing prompt; verifies exactly 1 LLM call made
  - No Spring context, no real API key, < 1s execution

**Deliverable:** First Embabel-managed agent replacing direct `ChatClient` calls. ✅
**Design notes:**
- Embabel requires Spring AI 1.1.1 (not 1.0.0); upgraded to maintain compatibility.
- `embabel-agent-starter-gemini` registers `gemini-2.5-flash` as a proper Embabel Llm bean.
  `embabel-agent-starter-openai` registers GPT model names which Gemini rejects — use Gemini.
- `spring-ai-starter-model-openai` (OpenAI compat endpoint) kept for `ChatController`.
  Two LLM paths coexist: ChatController → OpenAI compat → Gemini; ResearchAgent → Gemini native.

---

### Phase 3 — Tool Calling ✅
*Goal: give the agent real capabilities via Spring-bean tools.*

Tools to implement (in order of priority):

| Tool class | Method | Purpose |
|---|---|---|
| `WebSearchTool` | `searchWeb(query)` | Retrieve external facts |
| `MemoryTool` | `storeMemory(userId, note)` | Persist a note |
| `MemoryTool` | `lookupNotes(userId, query)` | Retrieve relevant notes |
| `NutritionCalcTool` | `calculateNutrition(food, quantity)` | Calorie/macro breakdown |

- [x] Implement each tool as a `@Component` Spring bean
- [x] Register tools with Embabel's tool registry / `@Action` declarations
- [x] Add `POST /api/coach-advice` endpoint that uses `CoachAgent` with tools

**Deliverable:** `POST /api/coach-advice` returns a typed `CoachAdvice` JSON backed by 3 tools. ✅
**Design notes:**
- Tool pattern used: *pre-fetch / augmented context* — Java code calls tools before the LLM call.
  LLM-directed tool calling (model decides when to invoke) deferred to Phase 7.
- `WebSearchTool` is a stub (keyword-keyed canned responses); Phase 10 RAG will replace it.
- `MemoryTool` uses an in-memory `ConcurrentHashMap`; Phase 4 will replace with JPA `AgentNote`.
- `NutritionCalcTool` uses a hard-coded USDA-based macro table; real API call optional upgrade.
- Mutating tool calls (`storeMemory`) happen *after* the LLM call (write-last safety principle).
- 27/27 unit tests pass (`mvn test`).

---

### Phase 4 — Memory Layer ✅
*Goal: stateful conversations and user profiles.*

Database tables (Flyway migrations):

```sql
-- V1__init.sql
CREATE TABLE conversation_message (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    role        VARCHAR(16) NOT NULL,   -- user | assistant | tool
    content     TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_profile (
    user_id          VARCHAR(64) PRIMARY KEY,
    display_name     VARCHAR(128),
    dietary_goals    TEXT,
    restrictions     TEXT,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_note (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    note_type  VARCHAR(32),
    content    TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [x] Create Flyway migrations V1 (schema) and V2 (dedup constraint)
  - Used `BIGINT GENERATED BY DEFAULT AS IDENTITY` instead of `BIGSERIAL` for H2+PostgreSQL compatibility
- [x] Create JPA entities: `ConversationMessage`, `AgentNote`, `UserProfile`
- [x] Create Spring Data repositories for all three entities
  - `AgentNoteRepository.existsByUserIdAndContent()` — app-level dedup guard
  - `ConversationMessageRepository.findTopNByUserIdOrderByCreatedAtDesc()` — sliding window
- [x] Implement `MemoryService`:
  - `saveMessage(userId, role, content)`
  - `getRecentMessages(userId, n)` → last N messages, oldest-first
  - `saveNote(userId, noteType, content)` — 3-layer dedup: existence check + UNIQUE constraint + `DataIntegrityViolationException` catch
  - `findNotes(userId, query)` → keyword match (upgrade to pgvector in Phase 10)
- [x] Refactor `MemoryTool` to delegate to `MemoryService` (removes `ConcurrentHashMap`)
- [x] Wire conversation history window into `ChatController` (last 8 turns injected into system prompt)
- [x] Enable Flyway in `application.yml`; switch `ddl-auto` from `create-drop` → `none`

**Deliverable:** Conversation history persists across HTTP calls; duplicate notes are silently deduplicated.

---

### Phase 5 — CoachAgent + Multi-step Flow ✅
*Goal: a second agent that builds on research output.*

- [x] Create `CoachAgent` that accepts a `ResearchBrief` as input and produces `CoachAdvice`
  - Added `coachFromResearch(ResearchBrief, Ai)` `@Action @AchievesGoal` method
  - Embabel GOAP auto-selects this path when ResearchBrief is in the blackboard
- [x] Create `CoachAdvice` structured output record (was in Phase 3, reused here)
- [x] Create a two-step workflow: ResearchAgent → CoachAgent
  - Explicit Java pipeline in `FullAdviceController` (`AgentInvocation` called twice)
- [x] Expose as `POST /api/full-advice` (topic → CoachAdvice)
  - `FullAdviceController` — validates `@NotBlank topic`, runs 2-step pipeline
- [x] Extract `MemoryService` interface; `JpaMemoryService` (prod) + `InMemoryMemoryService` (tests)
  - Maps to Mastra's backing-store abstraction (LibSQLStore vs InMemoryStorage)
- [x] 7 new tests in `CoachAgentPipelineTest` — 35 total passing
- [x] Postman folder `05 - Full Advice Pipeline` added with happy-path + error requests

---

### Phase 6 — Guardrails & Safety ✅
*Goal: prevent unsafe, low-quality, or injected output.*

- [x] Input sanitisation filter (`InputSanitiser` + `InputGuardrailFilter` — 11 regex patterns)
- [x] Output moderation check (`OutputModerator` — keyword patterns: medical claims, stop-medication, low-calorie)
- [x] `CriticAgent`: LLM-as-judge scoring (0-100) + `safe` flag; retry in `FullAdviceController` if score < 40
- [x] Rate limiting per `userId` (`RateLimiter` — sliding-window ConcurrentHashMap, 10 req/min default)
- [x] Authorization: `ApiKeyInterceptor` checks `X-Api-Key` header (configurable via `app.guardrail.api-key`)
- [x] `GuardrailExceptionHandler` maps exceptions to HTTP 400/401/422/429
- [x] Tests: `InputSanitiserTest` (12), `OutputModeratorTest` (7), `CriticAgentTest` (6), `RateLimiterTest` (6)
- [x] `app.guardrail.enabled: true` in `application.yml`; all checks gate-able by flag

Design decisions:
- No Spring Security added (HandlerInterceptor is sufficient for single API-key auth)
- `RateLimiter` is in-memory (resets on restart); Phase 9 can replace with Redis
- `CriticAgent` uses Embabel GOAP: `CriticScore` goal type does not conflict with `CoachAdvice`
- pom.xml `java.version` corrected from 25 → 21 (Temurin 21.0.3 installed)

---

### Phase 7 — Dynamic Agents ✅
*Goal: route to different models/tools based on user tier or query type.*

- [x] Add `UserTier` enum (`FREE`, `PREMIUM`) to `UserProfile` (Flyway V3 migration adds `tier` column)
- [x] Implement `AgentRouter` (`@Service`):
  - Short query (< 80 chars) + FREE → SINGLE_STEP (CoachAgent only)
  - Long query (≥ 80 chars) + PREMIUM → FULL_PIPELINE (ResearchAgent → CoachAgent)
  - SHORT + PREMIUM → SINGLE_STEP (fast response for simple questions)
  - FREE tier always → SINGLE_STEP (cost control)
- [x] `RouteAdviceResponse` record wraps `CoachAdvice` with `tier`, `route`, `model` metadata
- [x] `POST /api/route-advice` — new endpoint, reads `X-User-Tier` header, delegates to `AgentRouter`
- [x] Model names configurable via `app.agent.model.free` / `app.agent.model.premium` in `application.yml`
- [x] 11 tests in `AgentRouterTest` — 77 total passing

Design decisions:
- `AgentRouter` is `@Service`, not `@Agent`: routing logic is explicit Java, not GOAP-planned
- `classify(topic, tier)` is package-visible (not private) to allow direct unit testing without mocking AgentPlatform
- Model name is returned in the response for observability; actual per-request Embabel model switching deferred to Phase 11
- `X-User-Tier` header parsing is fail-safe: unknown values default to FREE

---

### Phase 8 — LoggerAgent
*Goal: dedicated agent for memory lifecycle management.*

- [ ] Create `LoggerAgent` as an `@Agent` that:
  - Summarises long conversation history into a compressed note
  - Extracts user preferences from messages and updates `UserProfile`
  - Tags and stores important facts as `agent_note` rows
- [ ] Schedule it to run asynchronously after each conversation turn

---

### Phase 9 — Observability
*Goal: structured traces for every agent action and tool call.*

- [ ] Add Micrometer + Spring Boot Actuator tracing
- [ ] Emit structured log events per agent action:
  - `agentName`, `actionName`, `userId`, `durationMs`, `status`, `promptVersion`
- [ ] Log tool calls: tool name, input hash (not raw PII), latency, result status
- [ ] Expose `/actuator/metrics` and `/actuator/health`
- [ ] Optional: integrate OpenTelemetry exporter for local Jaeger or Cloud Trace

---

### Phase 10 — RAG (Document-backed Knowledge)
*Goal: answer questions grounded in uploaded nutrition docs or notes.*

- [ ] Switch from H2 to PostgreSQL + add `pgvector` extension
- [ ] Add Spring AI vector store support (`PgVectorStore`)
- [ ] Create `DocumentIngestionService`:
  - Accept plain-text / PDF bytes
  - Chunk → embed (using Gemini embedding model or a local model)
  - Store vectors in pgvector table
- [ ] Create `RetrievalTool` that fetches top-K relevant chunks for a query
- [ ] Wire retrieval context into `ResearchAgent` prompts
- [ ] Add `POST /api/ingest` endpoint for document upload

**Phases of RAG complexity:**
1. Plain-text storage with keyword search (start here)
2. Add pgvector + embeddings
3. Add reranking pass
4. Agent-driven query rewriting

---

### Phase 11 — Multi-Agent Supervisor
*Goal: PlannerAgent orchestrates specialist agents.*

- [ ] Create `PlannerAgent` that:
  - Classifies the user query into a task type
  - Builds a plan (sequence of agent calls)
  - Dispatches to ResearchAgent, CoachAgent, LoggerAgent
  - Collects outputs and passes to CriticAgent for final review
- [ ] Implement retry logic when Critic score is below threshold
- [ ] Expose as `POST /api/plan` — the "smart" entry point

Multi-agent flow:

```
User query
  └─► PlannerAgent (classify + plan)
        ├─► ResearchAgent (gather facts)
        ├─► CoachAgent (produce advice)
        ├─► LoggerAgent (persist state)
        └─► CriticAgent (score + gate)
              └─► [retry loop if score < threshold]
                    └─► Final response
```

---

### Phase 12 — Streaming
*Goal: stream tokens to the client in real time.*

- [ ] Add `spring-boot-starter-webflux` 
- [ ] Create `POST /api/stream/advice` that returns `text/event-stream`
- [ ] Use `ChatClient.stream()` for token-by-token SSE
- [ ] Optionally add a minimal HTML+JS page that consumes the stream

---

### Phase 13 — Evals
*Goal: automated quality gates on LLM output.*

Eval categories:

| Eval | Check |
|---|---|
| Schema | Output deserialises into typed record without error |
| Grounding | Key facts in output appear in retrieved context |
| Safety | No banned medical claims / dangerous advice |
| Tool coverage | Expected tool was called at least once |
| Length | Summary within configured token/char bounds |

- [ ] Implement `EvalService` with assertion helpers
- [ ] Add JUnit tests per agent that run against a small fixture set
- [ ] CI gate: evals must pass before merging to main

---

### Phase 14 — MCP Server
*Goal: expose internal tools so any MCP-compatible client can call them.*

- [ ] Add Embabel MCP server configuration (SSE transport)
- [ ] Register `WebSearchTool`, `MemoryTool`, `NutritionCalcTool` as MCP tools
- [ ] Test from a separate MCP client (e.g. Claude Desktop or a test script)
- [ ] Document the tool schema in `mcp/README.md`

---

### Phase 15 — Packaging & Deployment
*Goal: runnable Docker image, production-safe config.*

- [ ] Write `Dockerfile` (multi-stage: build with Maven, run with JRE 21 slim)
- [ ] Write `docker-compose.yml` with app + PostgreSQL + pgvector
- [ ] Externalise all secrets via env vars (no secrets in codebase)
- [ ] Add `application-prod.yml` with connection pool tuning and log levels
- [ ] Health check at `/actuator/health` for container orchestrator
- [ ] Optional: Helm chart or Fly.io `fly.toml` for easy cloud deploy

---

## Key Maven Dependencies (reference)

```xml
<!-- Spring Boot parent -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.x</version>
</parent>

<!-- Spring AI BOM -->
<dependencyManagement>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.0.x</version>
    <type>pom</type>
    <scope>import</scope>
  </dependency>
</dependencyManagement>

<!-- Core starters -->
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator
spring-boot-starter-security      <!-- Phase 6+ -->
spring-boot-starter-webflux       <!-- Phase 12+ -->

<!-- Spring AI -->
spring-ai-google-gemini-spring-boot-starter
spring-ai-pgvector-store-spring-boot-starter   <!-- Phase 10+ -->

<!-- Embabel -->
com.embabel:embabel-agent-spring-boot-starter  <!-- check latest coordinates -->

<!-- DB -->
com.h2database:h2
org.postgresql:postgresql
org.flywaydb:flyway-core

<!-- Utilities -->
org.projectlombok:lombok
```

---

## application.yml Skeleton

```yaml
spring:
  # Spring AI 1.0.0 — use Google AI Studio's OpenAI-compatible endpoint
  ai:
    openai:
      api-key: ${GEMINI_API_KEY}
      base-url: https://generativelanguage.googleapis.com/v1beta/openai
      chat:
        options:
          model: gemini-2.0-flash          # start cheap; override per tier
  datasource:
    url: jdbc:h2:mem:nutritioncoach        # swap to postgres in prod
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: validate                   # Flyway owns the schema
  flyway:
    enabled: true

app:
  agent:
    model:
      free: gemini-1.5-flash
      premium: gemini-1.5-pro
    memory:
      window-size: 8
  guardrail:
    enabled: true
```

---

## Immediate Next Steps

1. **Create `pom.xml`** and verify the project compiles with Java 21.
2. **Add `application.yml`** with Gemini key env var placeholder.
3. **Create `NutritionCoachApplication.java`** and `ChatController.java`.
4. **Smoke-test** `POST /api/chat` with a hardcoded nutrition question.
5. **Then** move to Phase 1 (structured output + prompt templates).

This plan will evolve.  Each phase can be broken into its own sub-plan as work begins.
