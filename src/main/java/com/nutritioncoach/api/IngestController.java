package com.nutritioncoach.api;

import com.nutritioncoach.rag.DocumentIngestionService;
import com.nutritioncoach.rag.DocumentIngestionService.IngestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Phase 10 — IngestController
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Exposes the RAG document ingestion pipeline as a REST endpoint.
 *
 * ENDPOINTS:
 *   POST /api/ingest       – ingest a single plain-text document
 *   POST /api/ingest/batch – ingest multiple documents in one call
 *
 * MERN/Next.js analogy (Next.js App Router):
 *   // app/api/ingest/route.ts
 *   export async function POST(req: Request) {
 *     const { docName, content } = await req.json()
 *     const result = await ingestionService.ingestText(docName, content)
 *     return Response.json(result)
 *   }
 *
 * Input validation (@Valid + Bean Validation):
 *   @NotBlank on docName and content maps to Zod z.string().min(1) in Node.
 *   @Size(max=100_000) on content guards against very large uploads that
 *   would create hundreds of chunks and slow down the in-memory similarity search.
 *
 * Book ref: Chapter 17 — RAG: Chunking & Ingestion
 *   "The ingest API is the entry point for grounding your agent.  Treat it
 *    like a write path: validate inputs strictly and return actionable errors."
 *
 * Book ref: Chapter 20 — RAG: Synthesis (the output end of the ingestion contract)
 *   "What you ingest determines what the agent can ground its answers in.
 *    Good ingestion hygiene pays dividends at retrieval time."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api")
public class IngestController {

    private final DocumentIngestionService ingestionService;

    // MERN analogy: import { ingestionService } from '@/services/ingestion'
    // Constructor injection — same pattern as CoachController / ResearchController.
    public IngestController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // ══ POST /api/ingest ════════════════════════════════════════════════════

    /**
     * Ingest a single plain-text nutrition document.
     *
     * Request body:
     *   { "docName": "omega3-guide", "content": "Omega-3 fatty acids are..." }
     *
     * Response:
     *   200 OK  { "docName": "omega3-guide", "chunkCount": 3 }
     *   400 Bad Request — validation errors for blank/missing fields
     *
     * MERN analogy:
     *   const res = await fetch('/api/ingest', {
     *     method: 'POST',
     *     body: JSON.stringify({ docName, content }),
     *   })
     *   const { chunkCount } = await res.json()
     *
     * Book ref: Chapter 17 — RAG: Chunking & Ingestion
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResult> ingest(@Valid @RequestBody IngestRequest request) {
        IngestResult result = ingestionService.ingestText(request.docName(), request.content());
        return ResponseEntity.ok(result);
    }

    // ══ POST /api/ingest/batch ══════════════════════════════════════════════

    /**
     * Ingest multiple documents in a single HTTP call.
     *
     * Request body:
     *   [
     *     { "docName": "omega3-guide", "content": "..." },
     *     { "docName": "protein-facts", "content": "..." }
     *   ]
     *
     * Response:
     *   200 OK  [{ "docName": "omega3-guide", "chunkCount": 3 }, ...]
     *
     * MERN analogy:
     *   const res = await fetch('/api/ingest/batch', {
     *     method: 'POST',
     *     body: JSON.stringify(docs),
     *   })
     *
     * Book ref: Chapter 17 — RAG: Chunking & Ingestion
     *   "Batch ingestion is useful for bootstrapping the knowledge base
     *    from an existing corpus of documents."
     */
    @PostMapping("/ingest/batch")
    public ResponseEntity<List<IngestResult>> ingestBatch(
            @Valid @RequestBody List<@Valid IngestRequest> requests) {
        var rawDocs = requests.stream()
                .map(r -> new DocumentIngestionService.RawDocument(r.docName(), r.content()))
                .toList();
        return ResponseEntity.ok(ingestionService.ingestAll(rawDocs));
    }

    // ── Request record ─────────────────────────────────────────────────────

    /**
     * Validated request body for a single document ingestion.
     *
     * MERN analogy:
     *   const ingestSchema = z.object({
     *     docName: z.string().min(1),
     *     content: z.string().min(1).max(100_000),
     *   })
     *
     * @param docName  logical name / identifier for this document (used as metadata)
     * @param content  full plain-text content to ingest (max 100k chars ≈ 70 pages)
     */
    public record IngestRequest(
            @NotBlank(message = "docName must not be blank")
            String docName,

            @NotBlank(message = "content must not be blank")
            @Size(max = 100_000, message = "content must be at most 100,000 characters")
            String content
    ) {}
}
