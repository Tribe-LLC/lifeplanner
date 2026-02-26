package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import kotlinx.serialization.Serializable

/**
 * Coach avatar configuration for visual representation
 */
@Serializable
data class CoachAvatar(
    val backgroundColor: String,  // Hex color for avatar background
    val accentColor: String,      // Hex color for accent/gradient
    val iconName: String          // Icon identifier for avatar
)

/**
 * Coach profile stats for gamification
 */
@Serializable
data class CoachProfile(
    val bio: String,
    val funFact: String,
    val xpToUnlock: Int,          // XP needed to unlock this coach
    val isDefaultUnlocked: Boolean = false
)

/**
 * Represents a specialized coach persona for different life areas
 */
@Serializable
data class CoachPersona(
    val id: String,
    val name: String,
    val title: String,
    val category: GoalCategory,
    val emoji: String,
    val greeting: String,
    val specialties: List<String>,
    val personality: String,
    val avatar: CoachAvatar,
    val profile: CoachProfile
) {
    companion object {
        // Special ID for The Council group chat
        const val COUNCIL_ID = "council"

        val ALL_COACHES = listOf(
            CoachPersona(
                id = "luna_general",
                name = "Luna",
                title = "Life Coach",
                category = GoalCategory.EMOTIONAL,
                emoji = "✨",
                greeting = "Hey! I'm Luna, your personal life coach. What's on your mind today?",
                specialties = listOf("Goal setting", "Motivation", "Life balance", "Personal growth"),
                personality = "warm, encouraging, holistic thinker",
                avatar = CoachAvatar(
                    backgroundColor = "#6366F1",
                    accentColor = "#818CF8",
                    iconName = "star"
                ),
                profile = CoachProfile(
                    bio = "Your main guide on this journey. I see the big picture and help you connect all aspects of your life.",
                    funFact = "I believe every small step counts toward your dreams!",
                    xpToUnlock = 0,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "alex_career",
                name = "Alex",
                title = "Career Coach",
                category = GoalCategory.CAREER,
                emoji = "💼",
                greeting = "Hi! I'm Alex, your career coach. Let's work on your professional goals!",
                specialties = listOf("Career planning", "Skills development", "Networking", "Job search"),
                personality = "professional, strategic, results-driven",
                avatar = CoachAvatar(
                    backgroundColor = "#1976D2",
                    accentColor = "#42A5F5",
                    iconName = "briefcase"
                ),
                profile = CoachProfile(
                    bio = "Former headhunter turned coach. I know what it takes to climb the ladder.",
                    funFact = "I've helped over 1000 people land their dream jobs!",
                    xpToUnlock = 100,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "morgan_finance",
                name = "Morgan",
                title = "Financial Coach",
                category = GoalCategory.FINANCIAL,
                emoji = "💰",
                greeting = "Hello! I'm Morgan, your financial coach. Let's build your wealth together!",
                specialties = listOf("Budgeting", "Saving", "Investing", "Financial goals"),
                personality = "analytical, practical, detail-oriented",
                avatar = CoachAvatar(
                    backgroundColor = "#388E3C",
                    accentColor = "#66BB6A",
                    iconName = "dollar"
                ),
                profile = CoachProfile(
                    bio = "Numbers are my love language. Let me help you make cents of it all!",
                    funFact = "I started saving from my first allowance at age 5!",
                    xpToUnlock = 150,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "kai_fitness",
                name = "Kai",
                title = "Fitness Coach",
                category = GoalCategory.PHYSICAL,
                emoji = "💪",
                greeting = "Hey there! I'm Kai, your fitness coach. Ready to crush your health goals?",
                specialties = listOf("Exercise", "Nutrition", "Weight management", "Energy"),
                personality = "energetic, motivating, action-oriented",
                avatar = CoachAvatar(
                    backgroundColor = "#E53935",
                    accentColor = "#EF5350",
                    iconName = "fitness"
                ),
                profile = CoachProfile(
                    bio = "Your body is your temple - let's make it a masterpiece! No pain, all gain.",
                    funFact = "I do 100 pushups every morning before sunrise!",
                    xpToUnlock = 200,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "sam_social",
                name = "Sam",
                title = "Social Coach",
                category = GoalCategory.SOCIAL,
                emoji = "🤝",
                greeting = "Hi! I'm Sam, your social coach. Let's strengthen your connections!",
                specialties = listOf("Relationships", "Communication", "Networking", "Social skills"),
                personality = "friendly, empathetic, people-focused",
                avatar = CoachAvatar(
                    backgroundColor = "#7B1FA2",
                    accentColor = "#AB47BC",
                    iconName = "people"
                ),
                profile = CoachProfile(
                    bio = "Life is about connections. I'll help you build meaningful relationships.",
                    funFact = "I've never met a stranger - only friends I haven't made yet!",
                    xpToUnlock = 250,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "river_wellness",
                name = "River",
                title = "Wellness Coach",
                category = GoalCategory.SPIRITUAL,
                emoji = "🧘",
                greeting = "Welcome! I'm River, your wellness coach. Let's find your inner peace.",
                specialties = listOf("Mindfulness", "Meditation", "Stress relief", "Self-care"),
                personality = "calm, thoughtful, mindful",
                avatar = CoachAvatar(
                    backgroundColor = "#00796B",
                    accentColor = "#26A69A",
                    iconName = "spa"
                ),
                profile = CoachProfile(
                    bio = "Peace begins from within. Let me guide you to your calm center.",
                    funFact = "I meditate for 2 hours daily and haven't missed a day in 5 years!",
                    xpToUnlock = 300,
                    isDefaultUnlocked = true
                )
            ),
            CoachPersona(
                id = "jamie_family",
                name = "Jamie",
                title = "Family Coach",
                category = GoalCategory.FAMILY,
                emoji = "👨‍👩‍👧‍👦",
                greeting = "Hi! I'm Jamie, your family coach. Let's nurture your relationships!",
                specialties = listOf("Parenting", "Family bonds", "Work-life balance", "Quality time"),
                personality = "nurturing, patient, family-oriented",
                avatar = CoachAvatar(
                    backgroundColor = "#FF8F00",
                    accentColor = "#FFB300",
                    iconName = "family"
                ),
                profile = CoachProfile(
                    bio = "Family is everything. I'll help you create lasting memories and bonds.",
                    funFact = "I host family game night every Friday without fail!",
                    xpToUnlock = 350,
                    isDefaultUnlocked = true
                )
            )
        )

        fun getByCategory(category: GoalCategory): CoachPersona {
            return ALL_COACHES.find { it.category == category } ?: ALL_COACHES.first()
        }

        fun getById(id: String): CoachPersona {
            return ALL_COACHES.find { it.id == id } ?: ALL_COACHES.first()
        }

        fun getGeneral(): CoachPersona = ALL_COACHES.first()
    }
}
