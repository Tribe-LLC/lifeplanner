package az.tribe.lifeplanner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Story(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val category: String,
    @SerialName("gradient_start") val gradientStart: String,
    @SerialName("gradient_end") val gradientEnd: String,
    @SerialName("cta_text") val ctaText: String? = null,
    @SerialName("cta_action") val ctaAction: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0
)
