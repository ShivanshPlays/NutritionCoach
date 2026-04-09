package com.nutritioncoach.memory;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * UserProfile — JPA entity for long-term / semantic memory
 * ══════════════════════════════════════════════════════════════════════════
 *
 * One row per user. Stores stable preferences and dietary goals.
 * Phase 4 seeds a default row for the placeholder "default" user.
 * Phase 6 (auth) makes userId come from the authenticated principal.
 * Phase 8 (LoggerAgent) updates this row automatically from conversation.
 *
 * MERN/Next.js analogy:
 *   Equivalent to a Prisma model:
 *     model UserProfile {
 *       userId       String  @id
 *       displayName  String?
 *       dietaryGoals String?
 *       restrictions String?
 *       updatedAt    DateTime @updatedAt
 *     }
 *
 * Book ref: Chapter 7 — Memory
 *   "Semantic memory = stable facts about the user (name, goals, preferences).
 *    Retrieved once per session and injected into the system prompt."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "dietary_goals", columnDefinition = "TEXT")
    private String dietaryGoals;

    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    protected UserProfile() {}

    public UserProfile(String userId) {
        this.userId = userId;
    }

    // Fluent setters — MERN analogy: Object.assign(profile, updates)
    public UserProfile displayName(String name)       { this.displayName = name;  return this; }
    public UserProfile dietaryGoals(String goals)     { this.dietaryGoals = goals; return this; }
    public UserProfile restrictions(String restr)     { this.restrictions = restr; return this; }

    public String getUserId()       { return userId; }
    public String getDisplayName()  { return displayName; }
    public String getDietaryGoals() { return dietaryGoals; }
    public String getRestrictions() { return restrictions; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
