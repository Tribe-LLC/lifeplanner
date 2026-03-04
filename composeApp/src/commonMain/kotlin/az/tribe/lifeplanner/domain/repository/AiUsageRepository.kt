package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.AiUsageStats

interface AiUsageRepository {
    suspend fun getMonthlyStats(): AiUsageStats
}
