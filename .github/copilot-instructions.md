# GitHub Copilot Instructions — NutritionCoach

## Project purpose
This project is a **learning exercise** in Spring Boot and Spring AI, built by someone
coming from the MERN stack / Next.js background.  Every coding task is also an
opportunity to map the corresponding concept from the
**"Principles of AI Engineering, 2nd Edition (Mastra AI book)"** to this Java implementation.

---

## Rules for Copilot

### 1 — Always maintain pedagogical comments
When writing or editing any Java, YAML, or config file:

- Add `// MERN/Next.js analogy:` comments wherever a concept exists in both worlds
  but uses Spring-specific vocabulary.  Be concrete: show the JS equivalent inline.
- Add `// Book ref: Chapter N — <topic>` comments next to every concept that
  appears in the Mastra AI book.
- Keep comments educational but concise — one paragraph max per concept.

### 2 — Map MERN/Next.js → Spring Boot vocabulary
Use these mappings consistently in comments and explanations:

| MERN / Next.js concept | Spring Boot equivalent |
|---|---|
| `express()` / `next.config.js` bootstrap | `@SpringBootApplication` main class |
| Express `router.get/post(...)` | `@GetMapping` / `@PostMapping` on `@RestController` |
| `export async function GET/POST` (App Router) | Same `@GetMapping` / `@PostMapping` |
| `req.body` / `await request.json()` | `@RequestBody` parameter |
| Zod schema validation | `@Valid` + Bean Validation annotations (`@NotBlank`, etc.) |
| Manual `import` and `new Service()` | Spring Dependency Injection (constructor injection) |
| `.env` + `next.config.js` | `application.yml` |
| Vercel AI SDK `generateText()` | `ChatClient.prompt().call().content()` |
| Vercel AI SDK `generateObject()` + Zod | `ChatClient.prompt().call().entity(MyRecord.class)` |
| AI SDK `streamText()` | `ChatClient.prompt().stream()` |
| Mastra `Agent` class | Embabel `@Agent` + `@Action` methods |
| Mastra tool (`createTool`) | Spring `@Component` bean registered as an LLM tool |
| Mastra memory / `LibSQLStore` | Spring Data JPA entities + `MemoryService` |
| Next.js API middleware | Spring `OncePerRequestFilter` / `HandlerInterceptor` |
| Vercel AI SDK `embed()` | Spring AI `EmbeddingClient` |

### 3 — Always update plan.md
After each task, update the relevant phase checklist in `plan.md`:
- Mark completed items with `[x]`
- Add short notes on any design decisions or deviations

### 4 — Always update BOOK_PROGRESS.md
After implementing anything that maps to a book chapter:
- Update the **Status** column of that chapter's row
- Add links to the implementing files in the **Files** column
- Add a one-line note in the **Notes** column

### 5 — Align implementation order with the book
Do not jump ahead to later book chapters unless the current chapter's
implementation is complete or explicitly deferred.  The build order in `plan.md`
mirrors the book's conceptual progression.

### 6 — Keep it simple
Follow the Implementation Discipline principle: only add what is needed for the
current phase.  No premature abstractions, no speculative features.

---

## Project stack at a glance

```
Java 21 (think: TypeScript with strict types, but compiled)
Spring Boot 3.4.4 (think: Express + Next.js framework combined)
Spring AI 1.0.0 (think: Vercel AI SDK for Java)
Gemini via Google AI Studio OpenAI-compatible endpoint
H2 in-memory DB now → PostgreSQL + pgvector later
Flyway (think: Prisma migrations, but pure SQL)
Maven (think: npm/pnpm, but for Java)
Embabel (think: Mastra agent orchestration, but on JVM — Phase 2+)
```

## Key env var
```bash
export GEMINI_API_KEY=<your Google AI Studio key>
```
