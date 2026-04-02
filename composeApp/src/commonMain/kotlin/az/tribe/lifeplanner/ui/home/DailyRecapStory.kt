package az.tribe.lifeplanner.ui.home

import az.tribe.lifeplanner.domain.model.Story
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
import kotlinx.datetime.LocalDate

/**
 * Generates a personalized daily recap Story from yesterday's actual data.
 * Story ID is date-keyed ("daily_recap_2026-03-26") so it appears as new every morning.
 *
 * @param yesterdaySteps step count from yesterday (null if health not connected)
 * @param yesterdaySleep sleep hours from yesterday (null if health not connected)
 */
fun generateDailyRecapStory(
    userProgress: UserProgress?,
    habits: List<HabitWithStatus>,
    today: LocalDate,
    yesterdaySteps: Long? = null,
    yesterdaySleep: Double? = null
): Story {
    val storyId = "daily_recap_$today"

    // weeklyCompletions is Mon(0)–Sun(6). Find yesterday's index.
    val todayIndex = today.dayOfWeek.ordinal // Mon=0 … Sun=6
    val yesterdayIndex = if (todayIndex == 0) 6 else todayIndex - 1
    val yesterdayName = dayName(yesterdayIndex)

    val totalHabits = habits.size
    val habitsYesterday = habits.count { it.weeklyCompletions.getOrNull(yesterdayIndex) == true }

    val streak = userProgress?.currentStreak ?: 0
    val level = userProgress?.currentLevel ?: 1
    val goalsCompleted = userProgress?.goalsCompleted ?: 0

    // Performance tier
    val completionRate = if (totalHabits > 0) habitsYesterday.toFloat() / totalHabits else -1f
    val (gradientStart, gradientEnd, emoji) = when {
        totalHabits == 0 -> Triple("#6366F1", "#818CF8", "✨")
        completionRate >= 1f -> Triple("#10B981", "#34D399", "🏆")
        completionRate >= 0.6f -> Triple("#F59E0B", "#FBBF24", "⭐")
        completionRate > 0f -> Triple("#8B5CF6", "#A78BFA", "💜")
        else -> Triple("#EC4899", "#F472B6", "💪")
    }

    val title = when {
        totalHabits == 0 -> "Ready for Today?"
        completionRate >= 1f -> "Perfect $yesterdayName!"
        completionRate >= 0.6f -> "$yesterdayName's Recap"
        completionRate > 0f -> "Keep Building"
        else -> "New Day, New Start"
    }

    val subtitle = buildString {
        when {
            totalHabits == 0 -> {
                if (streak > 0) append("🔥 $streak-day streak going strong")
                else append("Add habits to start tracking your daily wins.")
            }
            completionRate >= 1f -> {
                append("You completed all $totalHabits habits yesterday")
                if (streak > 1) append(" and kept your 🔥 $streak-day streak alive")
                append(". Incredible consistency!")
            }
            completionRate >= 0.6f -> {
                append("$habitsYesterday of $totalHabits habits done yesterday")
                if (streak > 0) append(" · 🔥 $streak-day streak")
                append(". Every rep counts.")
            }
            completionRate > 0f -> {
                append("$habitsYesterday habit${if (habitsYesterday > 1) "s" else ""} checked in yesterday")
                append(". Today is a chance to do more.")
            }
            else -> {
                append("Yesterday was quiet — but today counts.")
                if (streak > 0) append(" Your 🔥 $streak-day streak shows you've got this.")
            }
        }
        if (goalsCompleted > 0 && totalHabits == 0) {
            append(" $goalsCompleted goal${if (goalsCompleted > 1) "s" else ""} completed so far.")
        }
        if (level > 1) append(" · Level $level")

        // Health highlights
        val healthParts = buildList {
            if (yesterdaySteps != null) {
                val stepsIcon = if (yesterdaySteps >= 10_000) "👟" else "🚶"
                add("$stepsIcon ${formatSteps(yesterdaySteps)} steps")
            }
            if (yesterdaySleep != null) {
                add("😴 ${formatSleep(yesterdaySleep)}h sleep")
            }
        }
        if (healthParts.isNotEmpty()) {
            append("\n${healthParts.joinToString(" · ")}")
        }
    }

    val (ctaText, ctaAction) = when {
        totalHabits == 0 -> "Add your first habit" to "add_habit"
        completionRate < 1f -> "Check in today" to "habits"
        else -> "Keep the streak going" to "habits"
    }

    return Story(
        id = storyId,
        title = title,
        subtitle = subtitle,
        emoji = emoji,
        category = "recap",
        gradientStart = gradientStart,
        gradientEnd = gradientEnd,
        ctaText = ctaText,
        ctaAction = ctaAction,
        sortOrder = -1
    )
}

/**
 * Returns a small rotation of curated tip/fact stories that changes every day.
 * Content is gossip-style — interesting science, surprising stats, counter-intuitive facts
 * about habits, productivity, and personal growth. No "check in" filler.
 */
fun getCuratedTipStories(today: LocalDate): List<Story> {
    val allTips = listOf(
        Story(
            id = "tip_66days",
            title = "It's Not 21 Days",
            subtitle = "The popular idea that habits form in 21 days is a myth. Research from University College London found the real average is 66 days — and it varies wildly: 18 to 254 days depending on the person and habit.",
            emoji = "📅",
            category = "science",
            gradientStart = "#4F46E5",
            gradientEnd = "#7C3AED",
            ctaText = null,
            ctaAction = null,
            sortOrder = 10
        ),
        Story(
            id = "tip_compound",
            title = "1% Better Every Day",
            subtitle = "If you improve 1% each day for a year, you end up 37× better. If you decline 1% each day, you're almost at zero. James Clear calls this the aggregation of marginal gains — tiny edges compound into massive results.",
            emoji = "📈",
            category = "science",
            gradientStart = "#059669",
            gradientEnd = "#10B981",
            ctaText = null,
            ctaAction = null,
            sortOrder = 11
        ),
        Story(
            id = "tip_2min_rule",
            title = "The 2-Minute Rule",
            subtitle = "Any habit can be started in 2 minutes. \"Read before bed\" → open the book. \"Run 5km\" → put on shoes. The goal is to make starting frictionless — momentum takes care of the rest.",
            emoji = "⏱️",
            category = "tips",
            gradientStart = "#DC2626",
            gradientEnd = "#EF4444",
            ctaText = "Add a habit",
            ctaAction = "add_habit",
            sortOrder = 12
        ),
        Story(
            id = "tip_temptation_bundle",
            title = "Pair Pain With Pleasure",
            subtitle = "\"Temptation bundling\" links a habit you struggle with to something you enjoy. Only listen to your favorite podcast while exercising. Only watch your show while folding laundry. It works.",
            emoji = "🎧",
            category = "tips",
            gradientStart = "#D97706",
            gradientEnd = "#F59E0B",
            ctaText = null,
            ctaAction = null,
            sortOrder = 13
        ),
        Story(
            id = "tip_sleep_memory",
            title = "Sleep Consolidates Skills",
            subtitle = "While you sleep, your brain replays the day's learning and transfers it to long-term memory. Skipping sleep after learning something new can erase up to 40% of what you studied. Sleep is literally part of the skill.",
            emoji = "🧠",
            category = "science",
            gradientStart = "#1D4ED8",
            gradientEnd = "#3B82F6",
            ctaText = null,
            ctaAction = null,
            sortOrder = 14
        ),
        Story(
            id = "tip_planning_fallacy",
            title = "You're Too Optimistic",
            subtitle = "The \"planning fallacy\" is real — humans consistently underestimate how long tasks take, even with past experience. The fix? Multiply your time estimate by 1.5× and add a buffer. You'll be closer to right.",
            emoji = "🗓️",
            category = "science",
            gradientStart = "#7C3AED",
            gradientEnd = "#A78BFA",
            ctaText = "Review goals",
            ctaAction = "goals",
            sortOrder = 15
        ),
        Story(
            id = "tip_progress_principle",
            title = "Small Wins Fuel Big Ones",
            subtitle = "Harvard research found that the single biggest daily motivator is \"the progress principle\" — making even tiny forward movement on meaningful work. Logging a small win activates the same reward circuits as a major milestone.",
            emoji = "🏅",
            category = "science",
            gradientStart = "#0284C7",
            gradientEnd = "#38BDF8",
            ctaText = null,
            ctaAction = null,
            sortOrder = 16
        ),
        Story(
            id = "tip_implementation_intention",
            title = "\"When X, I Will Y\"",
            subtitle = "Studies show that writing down \"I will [habit] at [time] in [place]\" doubles or triples the chance of follow-through. It's called an implementation intention — and it takes under 30 seconds to create.",
            emoji = "✍️",
            category = "tips",
            gradientStart = "#047857",
            gradientEnd = "#10B981",
            ctaText = null,
            ctaAction = null,
            sortOrder = 17
        ),
        Story(
            id = "tip_social_commitment",
            title = "Tell Someone",
            subtitle = "Publicly committing to a goal increases completion rates by up to 65%. Adding accountability — a friend, a coach, or just logging it — bumps that to 95%. The act of being seen changes the game.",
            emoji = "🤝",
            category = "tips",
            gradientStart = "#9333EA",
            gradientEnd = "#C084FC",
            ctaText = "Chat with coach",
            ctaAction = "ai_chat",
            sortOrder = 18
        ),
        Story(
            id = "tip_goldilocks",
            title = "The Goldilocks Zone",
            subtitle = "Motivation peaks when a task is just slightly above your current ability — not too easy, not too hard. Neuroscientist Andrew Huberman calls this \"the optimal challenge point.\" This is why leveling up keeps things engaging.",
            emoji = "🎯",
            category = "science",
            gradientStart = "#B45309",
            gradientEnd = "#F59E0B",
            ctaText = null,
            ctaAction = null,
            sortOrder = 19
        ),
        Story(
            id = "tip_identity",
            title = "\"I Am\" vs \"I Want\"",
            subtitle = "People who say \"I am a runner\" are more likely to stick to running than those who say \"I want to run more.\" Identity-based habits are stickier because every action becomes a vote for who you are.",
            emoji = "🪞",
            category = "science",
            gradientStart = "#0E7490",
            gradientEnd = "#22D3EE",
            ctaText = null,
            ctaAction = null,
            sortOrder = 20
        ),
        Story(
            id = "tip_decision_fatigue",
            title = "Decisions Drain You",
            subtitle = "Decision fatigue is real — the more choices you make throughout the day, the worse your willpower gets. High performers automate low-stakes decisions (meals, outfits, routines) to save mental energy for what matters.",
            emoji = "⚡",
            category = "science",
            gradientStart = "#7C3AED",
            gradientEnd = "#6366F1",
            ctaText = null,
            ctaAction = null,
            sortOrder = 21
        )
    )

    // Rotate 3 tips per day based on day of year
    val dayOfYear = today.toEpochDays().toInt()
    val step = 3
    val startIndex = (dayOfYear * step) % allTips.size
    return (0 until step).map { i -> allTips[(startIndex + i) % allTips.size] }
}

private fun dayName(index: Int): String = when (index) {
    0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"
    3 -> "Thursday"; 4 -> "Friday"; 5 -> "Saturday"
    else -> "Sunday"
}

/** Formats step count with thousands separator, KMP-safe. */
private fun formatSteps(steps: Long): String {
    val s = steps.toString()
    return buildString {
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) append(',')
            append(c)
        }
    }
}

/** Formats sleep hours to 1 decimal place, KMP-safe. */
private fun formatSleep(hours: Double): String {
    val whole = hours.toLong()
    val decimal = ((hours - whole) * 10).toLong()
    return "$whole.$decimal"
}
