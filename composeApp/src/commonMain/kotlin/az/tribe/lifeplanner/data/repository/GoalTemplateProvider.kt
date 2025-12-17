package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.GoalTemplate
import az.tribe.lifeplanner.domain.model.TemplateDifficulty

object GoalTemplateProvider {

    fun getAllTemplates(): List<GoalTemplate> = buildList {
        addAll(careerTemplates)
        addAll(financialTemplates)
        addAll(physicalTemplates)
        addAll(socialTemplates)
        addAll(emotionalTemplates)
        addAll(spiritualTemplates)
        addAll(familyTemplates)
    }

    fun getTemplatesByCategory(category: GoalCategory): List<GoalTemplate> {
        return when (category) {
            GoalCategory.CAREER -> careerTemplates
            GoalCategory.FINANCIAL -> financialTemplates
            GoalCategory.PHYSICAL -> physicalTemplates
            GoalCategory.SOCIAL -> socialTemplates
            GoalCategory.EMOTIONAL -> emotionalTemplates
            GoalCategory.SPIRITUAL -> spiritualTemplates
            GoalCategory.FAMILY -> familyTemplates
        }
    }

    fun getTemplateById(id: String): GoalTemplate? {
        return getAllTemplates().find { it.id == id }
    }

    private val careerTemplates = listOf(
        GoalTemplate(
            id = "career_promotion",
            category = GoalCategory.CAREER,
            title = "Get a Promotion",
            description = "Work towards earning a promotion at your current job by developing skills and demonstrating value",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Identify promotion requirements",
                "Complete relevant training/certifications",
                "Take on additional responsibilities",
                "Document achievements and impact",
                "Schedule meeting with manager"
            ),
            icon = "📈",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("career growth", "professional development")
        ),
        GoalTemplate(
            id = "career_new_skill",
            category = GoalCategory.CAREER,
            title = "Learn a New Professional Skill",
            description = "Master a new skill that will advance your career and make you more valuable",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Research and choose the skill to learn",
                "Find learning resources (course, books, mentor)",
                "Complete introductory training",
                "Practice with real projects",
                "Apply skill at work"
            ),
            icon = "🎯",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("learning", "skills")
        ),
        GoalTemplate(
            id = "career_job_switch",
            category = GoalCategory.CAREER,
            title = "Land a New Job",
            description = "Find and secure a new position that better aligns with your career goals",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Update resume and LinkedIn profile",
                "Define target roles and companies",
                "Apply to 10+ relevant positions",
                "Practice interview skills",
                "Negotiate and accept offer"
            ),
            icon = "💼",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("job search", "career change")
        ),
        GoalTemplate(
            id = "career_side_business",
            category = GoalCategory.CAREER,
            title = "Start a Side Business",
            description = "Launch a side project or business to generate additional income or pursue a passion",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Validate business idea",
                "Create business plan",
                "Set up legal and financial basics",
                "Launch minimum viable product",
                "Acquire first customers"
            ),
            icon = "🚀",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("entrepreneurship", "passive income")
        )
    )

    private val financialTemplates = listOf(
        GoalTemplate(
            id = "financial_emergency_fund",
            category = GoalCategory.FINANCIAL,
            title = "Build Emergency Fund",
            description = "Save 3-6 months of expenses for financial security and peace of mind",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Calculate monthly expenses",
                "Set up dedicated savings account",
                "Save first month's expenses",
                "Reach 3 months saved",
                "Complete 6 months fund"
            ),
            icon = "🏦",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("savings", "financial security")
        ),
        GoalTemplate(
            id = "financial_debt_free",
            category = GoalCategory.FINANCIAL,
            title = "Become Debt-Free",
            description = "Pay off all consumer debt and achieve financial freedom",
            suggestedTimeline = GoalTimeline.LONG_TERM,
            suggestedMilestones = listOf(
                "List all debts with interest rates",
                "Create debt payoff strategy",
                "Pay off smallest debt",
                "Pay off highest-interest debt",
                "Celebrate being debt-free"
            ),
            icon = "💳",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("debt", "financial freedom")
        ),
        GoalTemplate(
            id = "financial_budget",
            category = GoalCategory.FINANCIAL,
            title = "Master Monthly Budgeting",
            description = "Create and stick to a monthly budget to take control of your finances",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Track all expenses for one month",
                "Create budget categories",
                "Set up budgeting app or system",
                "Follow budget for 30 days",
                "Review and optimize budget"
            ),
            icon = "📊",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("budgeting", "money management")
        ),
        GoalTemplate(
            id = "financial_invest",
            category = GoalCategory.FINANCIAL,
            title = "Start Investing",
            description = "Begin building wealth through regular investing in stocks, ETFs, or retirement accounts",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Learn investing basics",
                "Open brokerage account",
                "Make first investment",
                "Set up automatic contributions",
                "Review and rebalance portfolio"
            ),
            icon = "📈",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("investing", "wealth building")
        )
    )

    private val physicalTemplates = listOf(
        GoalTemplate(
            id = "physical_weight_loss",
            category = GoalCategory.PHYSICAL,
            title = "Lose Weight Healthily",
            description = "Achieve a healthy weight through sustainable diet and exercise habits",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Consult with healthcare provider",
                "Create meal plan",
                "Establish exercise routine",
                "Lose first 5 pounds",
                "Reach target weight"
            ),
            icon = "⚖️",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("weight loss", "health")
        ),
        GoalTemplate(
            id = "physical_run_5k",
            category = GoalCategory.PHYSICAL,
            title = "Run a 5K",
            description = "Train to complete a 5K run, building endurance and fitness",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Get proper running shoes",
                "Complete Couch to 5K Week 1",
                "Run 2K without stopping",
                "Run 4K without stopping",
                "Complete 5K race"
            ),
            icon = "🏃",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("running", "fitness")
        ),
        GoalTemplate(
            id = "physical_strength",
            category = GoalCategory.PHYSICAL,
            title = "Build Strength",
            description = "Develop physical strength through consistent weight training",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Learn proper form for key exercises",
                "Establish 3x weekly workout routine",
                "Increase weights by 20%",
                "Complete 8-week program",
                "Reach strength goals"
            ),
            icon = "💪",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("strength training", "gym")
        ),
        GoalTemplate(
            id = "physical_sleep",
            category = GoalCategory.PHYSICAL,
            title = "Improve Sleep Quality",
            description = "Develop healthy sleep habits for better rest and recovery",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Audit current sleep habits",
                "Set consistent sleep schedule",
                "Create bedtime routine",
                "Optimize sleep environment",
                "Achieve 7-8 hours consistently"
            ),
            icon = "😴",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("sleep", "wellness")
        )
    )

    private val socialTemplates = listOf(
        GoalTemplate(
            id = "social_network",
            category = GoalCategory.SOCIAL,
            title = "Expand Professional Network",
            description = "Build meaningful professional connections to advance your career",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Optimize LinkedIn profile",
                "Attend 3 networking events",
                "Reach out to 10 new contacts",
                "Schedule 5 coffee chats",
                "Nurture ongoing relationships"
            ),
            icon = "🤝",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("networking", "connections")
        ),
        GoalTemplate(
            id = "social_friendships",
            category = GoalCategory.SOCIAL,
            title = "Deepen Friendships",
            description = "Strengthen existing friendships and create more meaningful connections",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Identify 5 friends to reconnect with",
                "Schedule regular catch-ups",
                "Plan a group activity",
                "Be more present in conversations",
                "Express appreciation to friends"
            ),
            icon = "👥",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("friendships", "relationships")
        ),
        GoalTemplate(
            id = "social_public_speaking",
            category = GoalCategory.SOCIAL,
            title = "Master Public Speaking",
            description = "Overcome fear of public speaking and become a confident presenter",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Join Toastmasters or similar group",
                "Give first prepared speech",
                "Practice impromptu speaking",
                "Present at work meeting",
                "Speak at larger event"
            ),
            icon = "🎤",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("communication", "confidence")
        ),
        GoalTemplate(
            id = "social_community",
            category = GoalCategory.SOCIAL,
            title = "Get Involved in Community",
            description = "Make a positive impact by contributing to your local community",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Research local volunteer opportunities",
                "Volunteer for first event",
                "Commit to regular volunteering",
                "Join community organization",
                "Lead a community initiative"
            ),
            icon = "🌍",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("volunteering", "giving back")
        )
    )

    private val emotionalTemplates = listOf(
        GoalTemplate(
            id = "emotional_mindfulness",
            category = GoalCategory.EMOTIONAL,
            title = "Build Mindfulness Practice",
            description = "Develop a consistent meditation practice for mental clarity and emotional balance",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Learn basic meditation techniques",
                "Meditate 5 minutes daily for a week",
                "Increase to 10 minutes daily",
                "Complete 30-day streak",
                "Integrate mindfulness into daily life"
            ),
            icon = "🧘",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("meditation", "mental health")
        ),
        GoalTemplate(
            id = "emotional_stress",
            category = GoalCategory.EMOTIONAL,
            title = "Manage Stress Better",
            description = "Develop healthy coping mechanisms to reduce and manage stress",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Identify stress triggers",
                "Learn 3 stress-relief techniques",
                "Practice daily relaxation",
                "Set healthy boundaries",
                "Maintain work-life balance"
            ),
            icon = "🌿",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("stress management", "self-care")
        ),
        GoalTemplate(
            id = "emotional_confidence",
            category = GoalCategory.EMOTIONAL,
            title = "Build Self-Confidence",
            description = "Develop stronger self-belief and confidence in your abilities",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Identify limiting beliefs",
                "Start daily affirmations",
                "Step outside comfort zone weekly",
                "Celebrate small wins",
                "Track confidence growth"
            ),
            icon = "✨",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("self-esteem", "personal growth")
        ),
        GoalTemplate(
            id = "emotional_therapy",
            category = GoalCategory.EMOTIONAL,
            title = "Start Therapy Journey",
            description = "Begin working with a therapist to improve mental health and wellbeing",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Research therapy types and therapists",
                "Book first appointment",
                "Attend 4 sessions",
                "Work on assigned exercises",
                "Evaluate progress"
            ),
            icon = "💚",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("therapy", "mental health")
        )
    )

    private val spiritualTemplates = listOf(
        GoalTemplate(
            id = "spiritual_practice",
            category = GoalCategory.SPIRITUAL,
            title = "Develop Daily Spiritual Practice",
            description = "Create a consistent spiritual routine that brings meaning and peace",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Define what spirituality means to you",
                "Choose practices to explore",
                "Practice daily for one week",
                "Build 30-day habit",
                "Deepen practice with study"
            ),
            icon = "🕊️",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("spirituality", "mindfulness")
        ),
        GoalTemplate(
            id = "spiritual_gratitude",
            category = GoalCategory.SPIRITUAL,
            title = "Cultivate Gratitude",
            description = "Develop a gratitude practice to increase happiness and appreciation",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Start gratitude journal",
                "Write 3 things daily for a week",
                "Express gratitude to others",
                "Complete 30-day gratitude challenge",
                "Make gratitude a lifestyle"
            ),
            icon = "🙏",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("gratitude", "positivity")
        ),
        GoalTemplate(
            id = "spiritual_purpose",
            category = GoalCategory.SPIRITUAL,
            title = "Discover Life Purpose",
            description = "Explore and clarify your values, passions, and life purpose",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Complete values assessment",
                "Identify core passions",
                "Journal about meaningful moments",
                "Create personal mission statement",
                "Align daily actions with purpose"
            ),
            icon = "🌟",
            difficulty = TemplateDifficulty.HARD,
            tags = listOf("purpose", "meaning")
        ),
        GoalTemplate(
            id = "spiritual_nature",
            category = GoalCategory.SPIRITUAL,
            title = "Connect with Nature",
            description = "Spend more time in nature to improve wellbeing and spiritual connection",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Schedule weekly nature time",
                "Try forest bathing/mindful walking",
                "Visit 5 new natural places",
                "Start nature photography/journaling",
                "Plan overnight camping trip"
            ),
            icon = "🌲",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("nature", "outdoors")
        )
    )

    private val familyTemplates = listOf(
        GoalTemplate(
            id = "family_quality_time",
            category = GoalCategory.FAMILY,
            title = "Increase Quality Family Time",
            description = "Spend more meaningful time with family members",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Audit current family time",
                "Plan weekly family activity",
                "Establish device-free time",
                "Create family traditions",
                "Plan family vacation/outing"
            ),
            icon = "👨‍👩‍👧‍👦",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("family time", "bonding")
        ),
        GoalTemplate(
            id = "family_relationship",
            category = GoalCategory.FAMILY,
            title = "Strengthen Partner Relationship",
            description = "Invest in your romantic relationship to deepen connection",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Schedule regular date nights",
                "Improve communication habits",
                "Read relationship book together",
                "Plan special trip/experience",
                "Express appreciation daily"
            ),
            icon = "💑",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("relationship", "marriage")
        ),
        GoalTemplate(
            id = "family_parenting",
            category = GoalCategory.FAMILY,
            title = "Become a Better Parent",
            description = "Develop parenting skills to better support your children's growth",
            suggestedTimeline = GoalTimeline.MID_TERM,
            suggestedMilestones = listOf(
                "Read parenting book",
                "Establish consistent routines",
                "Practice active listening",
                "Create one-on-one time with each child",
                "Model healthy behaviors"
            ),
            icon = "👶",
            difficulty = TemplateDifficulty.MEDIUM,
            tags = listOf("parenting", "children")
        ),
        GoalTemplate(
            id = "family_reconnect",
            category = GoalCategory.FAMILY,
            title = "Reconnect with Extended Family",
            description = "Strengthen bonds with extended family members",
            suggestedTimeline = GoalTimeline.SHORT_TERM,
            suggestedMilestones = listOf(
                "Create family contact list",
                "Schedule monthly calls with relatives",
                "Plan family reunion/gathering",
                "Share family stories and history",
                "Create family group chat/album"
            ),
            icon = "🏠",
            difficulty = TemplateDifficulty.EASY,
            tags = listOf("extended family", "relatives")
        )
    )
}
