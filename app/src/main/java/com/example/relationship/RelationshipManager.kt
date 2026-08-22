package com.example.relationship

import com.example.data.database.entity.RelationshipState

class RelationshipManager {

    fun calculateNewState(
        currentState: RelationshipState,
        sessionLengthSec: Int,
        messageCount: Int,
        averageConfidence: Float,
        sentimentScore: Float // 1.0 = positive boost, -1.0 = negative/conflict drop
    ): RelationshipState {
        // Boost engagement based on length and message volume
        val engagementDelta = (sessionLengthSec / 120.0f + messageCount / 5.0f).coerceAtMost(5.0f)
        val targetEngagement = (currentState.engagementScore + engagementDelta).coerceIn(0f, 100f)

        // Warmth delta based on sentiment and interactive continuity
        val warmthDelta = (sentimentScore * 3.5f + (messageCount / 8f)).coerceAtMost(6.0f)
        val targetWarmth = (currentState.warmthScore + warmthDelta).coerceIn(0f, 100f)

        // Trust score builds over memories remembered and speaking safety, boosted by user confidence
        val trustDelta = (sentimentScore * 2.0f + (averageConfidence * 5.0f)).coerceAtMost(4.0f)
        val targetTrust = (currentState.trustScore + trustDelta).coerceIn(0f, 100f)

        // Companionship represents general aggregation of the active values
        val targetCompanionship = ((targetWarmth * 0.4f) + (targetTrust * 0.4f) + (targetEngagement * 0.2f)).coerceIn(0f, 100f)

        // Calculate unified Relationship Level (Increment levels based on companionship score tiers)
        val levelFactor = (targetCompanionship / 10).toInt() + 1

        return RelationshipState(
            userId = currentState.userId,
            relationshipLevel = levelFactor,
            trustScore = targetTrust,
            engagementScore = targetEngagement,
            warmthScore = targetWarmth,
            humorScore = currentState.humorScore,
            companionshipScore = targetCompanionship,
            totalConversations = currentState.totalConversations + 1,
            updatedAt = System.currentTimeMillis()
        )
    }
}
