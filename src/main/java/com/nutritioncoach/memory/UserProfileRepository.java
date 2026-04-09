package com.nutritioncoach.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for user profiles.
 *
 * MERN/Next.js analogy:
 *   Equivalent to a Prisma UserProfile model query layer.
 *   findByUserId → SELECT * FROM user_profile WHERE user_id = ?
 *   In Prisma: prisma.userProfile.findUnique({ where: { userId } })
 *
 * Book ref: Chapter 7 — Memory
 *   "Semantic memory = stable facts about the user fetched once per session."
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    /**
     * Find a user's profile by userId.  Returns empty Optional if no profile
     * has been set up yet (new user).
     *
     * MERN analogy: prisma.userProfile.findUnique({ where: { userId } })
     */
    Optional<UserProfile> findByUserId(String userId);
}
