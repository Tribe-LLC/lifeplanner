package az.tribe.lifeplanner.domain.model

data class AiUsageSummary(
    val provider: String,
    val requestCount: Int,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val estimatedCostUsd: Double
)

data class AiUsageStats(
    val totalRequests: Int,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val byProvider: List<AiUsageSummary>
)
