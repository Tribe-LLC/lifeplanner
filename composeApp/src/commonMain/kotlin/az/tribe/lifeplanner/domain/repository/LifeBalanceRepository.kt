package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.LifeBalanceReport
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.domain.model.LifeAreaScore
import az.tribe.lifeplanner.domain.model.ManualAssessment
import kotlinx.coroutines.flow.Flow

interface LifeBalanceRepository {

    /**
     * Calculate the current life balance scores based on goals, habits, and activity
     */
    suspend fun calculateCurrentBalance(): LifeBalanceReport

    /**
     * Get the score for a specific life area
     */
    suspend fun getAreaScore(area: LifeArea): LifeAreaScore

    /**
     * Get all area scores
     */
    suspend fun getAllAreaScores(): List<LifeAreaScore>

    /**
     * Generate AI insights and recommendations for improving balance
     */
    suspend fun generateAIInsights(areaScores: List<LifeAreaScore>): LifeBalanceReport

    /**
     * Save a manual assessment from the user
     */
    suspend fun saveManualAssessment(assessment: ManualAssessment)

    /**
     * Get all manual assessments
     */
    suspend fun getManualAssessments(): List<ManualAssessment>

    /**
     * Get historical balance reports
     */
    fun getBalanceHistory(): Flow<List<LifeBalanceReport>>

    /**
     * Save a balance report
     */
    suspend fun saveBalanceReport(report: LifeBalanceReport)

    /**
     * Get the most recent balance report
     */
    suspend fun getLatestReport(): LifeBalanceReport?
}
