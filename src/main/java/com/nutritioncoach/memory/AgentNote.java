package com.nutritioncoach.memory;

import jakarta.persistence.*;
import java.time.Instant;
import com.nutritioncoach.memory.NoteType;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * AgentNote — JPA entity for long-term / episodic memory
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Stores free-text notes created by agents during sessions.
 * Phase 4: retrieved via keyword match.
 * Phase 10: replaced by pgvector similarity search.
 *
 * MERN/Next.js analogy:
 *   Equivalent to a Prisma model:
 *     model AgentNote {
 *       id        Int      @id @default(autoincrement())
 *       userId    String
 *       noteType  String?
 *       content   String
 *       createdAt DateTime @default(now())
 *       @@unique([userId, content])   // dedup constraint (V2 migration)
 *     }
 *
 * Book ref: Chapter 7 — Memory
 *   "Long-term memory = persistent notes and user profile.
 *    Notes should be idempotent — writing the same note twice must be safe."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "agent_note",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_agent_note_user_content",
           columnNames = {"user_id", "content"}
       ))
public class AgentNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Closed vocabulary classifier — stored as VARCHAR via EnumType.STRING.
    // MERN analogy: noteType: NoteType  (TypeScript union type discriminator)
    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", length = 32)
    private NoteType noteType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    protected AgentNote() {}

    public AgentNote(String userId, NoteType noteType, String content) {
        this.userId = userId;
        this.noteType = noteType;
        this.content = content;
    }

    public Long getId()          { return id; }
    public String getUserId()    { return userId; }
    public NoteType getNoteType() { return noteType; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
