package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.enum.AiProvider
import az.tribe.lifeplanner.domain.model.AiUsageStats
import az.tribe.lifeplanner.domain.model.AiUsageSummary
import az.tribe.lifeplanner.domain.repository.AiUsageRepository
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AiUsageLogDto(
    val id: String = "",
    val provider: String = "",
    val model: String = "",
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("request_type") val requestType: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

class AiUsageRepositoryImpl(
    private val supabase: SupabaseClient
) : AiUsageRepository {

    override suspend fun getMonthlyStats(): AiUsageStats {
        return try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val startOfMonth = "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-01T00:00:00"

            val logs = supabase.postgrest["ai_usage_logs"]
                .select {
                    filter { gte("created_at", startOfMonth) }
                }
                .decodeList<AiUsageLogDto>()

            aggregateStats(logs)
        } catch (e: Exception) {
            Logger.w("AiUsageRepo") { "Failed to fetch usage stats: ${e.message}" }
            AiUsageStats(
                totalRequests = 0,
                totalTokens = 0,
                estimatedCostUsd = 0.0,
                byProvider = emptyList()
            )
        }
    }

    private fun aggregateStats(logs: List<AiUsageLogDto>): AiUsageStats {
        val byProvider = logs.groupBy { it.provider }.map { (provider, providerLogs) ->
            val inputTokens = providerLogs.sumOf { it.inputTokens.toLong() }
            val outputTokens = providerLogs.sumOf { it.outputTokens.toLong() }
            val cost = AiProvider.fromProviderName(provider)
                ?.estimateCost(inputTokens, outputTokens) ?: 0.0

            AiUsageSummary(
                provider = provider,
                requestCount = providerLogs.size,
                totalInputTokens = inputTokens,
                totalOutputTokens = outputTokens,
                estimatedCostUsd = cost
            )
        }.sortedByDescending { it.requestCount }

        return AiUsageStats(
            totalRequests = logs.size,
            totalTokens = logs.sumOf { (it.inputTokens + it.outputTokens).toLong() },
            estimatedCostUsd = byProvider.sumOf { it.estimatedCostUsd },
            byProvider = byProvider
        )
    }
}
