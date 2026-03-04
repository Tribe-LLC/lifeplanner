# Life Planner — Terminology & Feature Glossary

Quick-reference for all features, domain terms, and shorthand used in this project.

---

## Core Features

| Feature | Description |
|---|---|
| **Goals** | Goal CRUD, progress tracking (0–100%), milestones, archiving |
| **Milestones** | Sub-steps within a goal (title, due date, completion toggle) |
| **Habits** | Recurring habit tracking with streaks, daily check-ins, frequency options |
| **Journal** | Journal entries with mood, tags, linked goals/habits |
| **Journal Wizard** | Multi-step AI-assisted journal creation flow (mood → link → note → AI generate) |
| **Focus** | Pomodoro-style timer tied to a specific milestone, with ambient sounds and visual themes |
| **Coaches** | 7 built-in AI coach personas + user-created custom coaches + group "council" mode |
| **AI Chat** | Conversational sessions with a specific coach, with actionable suggestions |
| **Smart Goals** | AI generates SMART goals from a free-text description or a preset life scenario |
| **Templates** | Pre-built goal templates with suggested milestones and difficulty rating |
| **Gamification** | XP, levels, streaks, badges, challenges, quests |
| **Badges** | 51 one-time achievement unlocks across 6 categories |
| **Challenges** | Time-boxed XP tasks (daily / weekly / monthly / special) |
| **Reviews** | AI-generated periodic performance reports (weekly / monthly / quarterly / yearly) |
| **Retrospective** | Day-by-day activity snapshot — shows everything that happened on a chosen date |
| **Life Balance** | 8-area life wheel with scores (0–100), trends, AI insights and recommendations |
| **Reminders** | Scheduled notifications with smart timing, quiet hours, and frequency options |
| **Dependencies** | Goal-to-goal relationships (blocks, parent/child, supports, related) |
| **Dependency Graph** | Visual graph showing goal relationships and blocking chains |
| **Sync** | Bidirectional SQLite (local) ↔ Supabase (cloud) sync with soft-delete |
| **Backup** | Full app data export/import as JSON |
| **Onboarding** | First-launch wizard collecting symbol, priorities, age range, profession, mindset |
| **Profile** | User settings, AI provider selection, account management |
| **Analytics** | Goal statistics dashboard (by category, timeline, completion rate) |

---

## Life Categories

The 7 core `GoalCategory` values used across Goals, Habits, and Coaches:

| Category | Focus Area |
|---|---|
| **Career** | Professional growth, skills, work |
| **Financial** | Money management, savings, investing |
| **Physical** | Health, fitness, body |
| **Social** | Friendships, networking, relationships |
| **Emotional** | Mental health, self-awareness |
| **Spiritual** | Purpose, mindfulness, inner peace |
| **Family** | Family relationships, home life |

Life Balance adds an 8th area: **Personal Growth** (maps to Career as fallback).

---

## AI System

### Providers

| Provider | Model | Notes |
|---|---|---|
| **Gemini** | `gemini-2.0-flash` | Default provider |
| **OpenAI** | `gpt-4o-mini` | Alternative |
| **Grok** | `grok-4-1-fast` | Alternative |

All routed through the `ai-proxy` Supabase Edge Function (no client-side API keys).

### Built-in Coaches

| Name | ID | Specialty |
|---|---|---|
| **Luna** | `luna_general` | Emotional / Life Coach (general, warm) |
| **Alex** | `alex_career` | Career Coach (strategic, results-driven) |
| **Morgan** | `morgan_finance` | Financial Coach (analytical, practical) |
| **Kai** | `kai_fitness` | Fitness / Physical Coach (energetic, action-oriented) |
| **Sam** | `sam_social` | Social Coach (friendly, empathetic) |
| **River** | `river_wellness` | Spiritual / Wellness Coach (calm, mindful) |
| **Jamie** | `jamie_family` | Family Coach (nurturing, patient) |
| **Council** | `council` | Group chat mode — multiple coaches respond together |

### Custom Coach Personality Presets

`Motivating` · `Strict` · `Friendly` · `Professional` · `Empathetic` · `Analytical` · `Creative` · `Mindful`

### Coach Suggestions (actionable cards in chat)

- `CreateGoal` — suggests a goal with title, description, category, timeline, milestones
- `CreateHabit` — suggests a new habit
- `CreateJournalEntry` — suggests writing a journal entry
- `CheckInHabit` — suggests checking in on a specific habit
- `AskQuestion` — interactive multi-step questionnaire

### Life Scenarios (Smart Goal Generator)

Pre-built prompts: `New Year New Me` · `Level Up Career` · `Wedding Ready` · `Financial Freedom` · `Health Transformation` · `Better Balance`

---

## Gamification System

### XP Rewards

| Action | XP |
|---|---|
| Goal created | 5 |
| Goal completed | 50 |
| Milestone completed | 15 |
| Habit check-in | 5 |
| Habit streak bonus | 2 per streak day |
| Journal entry | 10 |
| Daily check-in | 5 |
| Perfect Day bonus | 25 |
| Focus 15 min | 10 |
| Focus 25 min | 20 |
| Focus 45 min | 30 |
| Focus 60 min | 40 |
| Streak bonus multiplier | +10% per streak day |

### Level Titles

| Level Range | Title |
|---|---|
| 1–4 | Novice |
| 5–9 | Beginner |
| 10–14 | Intermediate |
| 15–19 | Proficient |
| 20–24 | Advanced |
| 25–29 | Expert |
| 30–39 | Champion |
| 40–49 | Grandmaster |
| 50+ | Life Master |

### Badge Categories

| Category | Examples |
|---|---|
| **Streak** | First Step, On Fire (3d), Week Warrior (7d), Monthly Master (30d), Centurion (100d) |
| **Goals** | Goal Getter (1), High Achiever (5), Dream Chaser (10), Goal Master (25), Legend (50) |
| **Habits** | Habit Starter, Habit Builder (5), Perfect Week, Perfect Month |
| **Journal** | Reflection Starter (1), Thoughtful Soul (10), Deep Thinker (30) |
| **Category** | Balanced Life (all 8), Health Champion (5 physical), Career Climber (5 career) |
| **Special** | Early Bird (<7 AM), Night Owl (>10 PM), Comeback King (7d break), Perfectionist (100%) |
| **Focus** | First Focus (1), Hour Power (60m), Focus Pro (10), Deep Worker (50) |

### Challenge Types

| Tier | Duration | Examples |
|---|---|---|
| Daily | 1 day | Check In (+10), Write Journal (+15), Complete All Habits (+20) |
| Weekly | 7 days | Goal Crusher (3 goals, +50), Habit Master (5 days, +75), Milestone Hunter (+60) |
| Monthly | 30 days | Goal Finisher (+200), Streak Legend (+300), Life Balance (+250) |
| Special | Varies | Perfect Day (+100), Category Master (+150), Morning Momentum (+100) |

---

## Focus Timer

### Duration Presets
`15 min` · `25 min` · `30 min` · `45 min` · `60 min` — plus a **+5 button** to extend during a session

### Timer States
`Idle` → `Running` → `Paused` → `Completed` / `Cancelled`

### Ambient Sounds
`None` · `Rain` · `Forest` · `Lo-fi` · `White Noise`

### Focus Themes (visual backgrounds)
`Default` · `Rain` · `Forest` · `Fireplace` · `Ocean` · `Night Sky`

### Partial XP
If cancelled after ≥5 minutes: `elapsed_minutes × 0.5` XP

---

## Goal System

### Statuses
`Not Started` → `In Progress` → `Completed`

### Timelines
| Timeline | Duration |
|---|---|
| Short-term | 0–3 months |
| Mid-term | 3–9 months |
| Long-term | 9–24 months |

### Filters
`All` · `Active` (not completed) · `Completed`

### Dependency Types
| Type | Meaning |
|---|---|
| **Blocks** | Source must complete before target can start |
| **Blocked By** | Source is waiting on another goal |
| **Related** | Loosely connected, no hard dependency |
| **Parent Of** | Source is the parent; target is a sub-goal |
| **Child Of** | Source is a sub-goal of target |
| **Supports** | Completing source helps achieve target |

### Template Difficulty
`Easy` · `Medium` · `Hard`

---

## Journal

### Mood Scale
| Mood | Score | Emoji |
|---|---|---|
| Very Happy | 5 | 😄 |
| Happy | 4 | 🙂 |
| Neutral | 3 | 😐 |
| Sad | 2 | 😢 |
| Very Sad | 1 | 😞 |

### Built-in Prompt Categories
`Daily Reflection` · `Goal Reflection` · `Mood Exploration` · `Weekly Review`

---

## Reviews & Retrospective

### Review Types
`Weekly` · `Monthly` · `Quarterly` · `Yearly`

### Review Components
- **Highlights** — notable achievements (goal completed, streak achieved, level up, etc.)
- **Insights** — data-driven observations with trend direction (up/down/stable)
- **Recommendations** — actionable suggestions with priority (high/medium/low)
- **Stats** — numeric summary with comparison to previous period

### Retrospective (Day Snapshot)
Shows for any chosen date: habit summary, journal entries, focus sessions, goal changes, badges earned, dominant mood, total focus minutes.

---

## Life Balance

### 8 Life Areas
Career · Financial · Physical · Social · Emotional · Spiritual · Family · Personal Growth

### Balance Rating Scale
`Excellent` · `Good` · `Moderate` · `Needs Attention` · `Critical`

### Balance Trends
`Improving` · `Stable` · `Declining`

---

## Reminders

### Reminder Types
`Goal Check-in` · `Habit Reminder` · `Milestone Due` · `Goal Due` · `Daily Reflection` · `Weekly Review` · `Motivation` · `Custom`

### Frequencies
`Once` · `Daily` · `Weekdays` · `Weekends` · `Weekly` · `Monthly` · `Smart` (AI-determined)

---

## Sync & Data

### Sync States
`Idle` · `Syncing` · `Synced` · `Offline` · `Error`

### Sync Architecture
- Local: SQLDelight (SQLite)
- Cloud: Supabase (Postgres)
- Direction: Bidirectional push-then-pull
- Conflict resolution: Version counter (`sync_version`)
- Deletion: Soft-delete (`is_deleted` flag)
- Trigger: Debounced 2-second delay after any mutation

### Backup Format
JSON export containing: goals, milestones, habits, habit completions, journal entries, user progress, badges, challenges, settings. Schema version: 1.

---

## Authentication States

| State | Meaning |
|---|---|
| **Loading** | Checking auth status |
| **Authenticated** | Signed in with Firebase + Supabase |
| **Guest** | Using the app without signing in |
| **Unauthenticated** | Not signed in and not guest |

---

## Navigation Routes

| Screen | Route |
|---|---|
| Home | `home` |
| Goals | `goals` |
| Goal Detail | `goal_detail/{goalId}` |
| Add Goal | `add_goal` |
| Edit Goal | `edit_goal/{goalId}` |
| Habit Tracker | `habit_tracker` |
| Journal | `journal` |
| Journal Wizard | `journal_wizard` |
| Journal Entry Detail | `journal_entry_detail/{entryId}` |
| Achievements | `achievements` |
| AI Chat | `ai_chat` |
| AI Chat Session | `ai_chat/{sessionId}` |
| Focus Setup | `focus_setup` |
| Retrospective | `retrospective` |
| Reviews | `reviews` |
| Life Balance | `life_balance` |
| Dependency Graph | `dependency_graph` |
| Templates | `templates` |
| Reminders | `reminders` |
| Analytics | `analytics` |
| Profile | `profile` |
| Backup Settings | `backup_settings` |
| Onboarding | `onboarding` |
| Smart Goal Generator | `ai_goal_generation` |
| Create Coach | `create_coach` |
| Create Group | `create_group` |

---

## UI Components

| Component | Description |
|---|---|
| **NextActionCard** | Home screen priority card (goal due today / next habit / continue goal / all caught up) |
| **QuickActionsPillRow** | Horizontal pill buttons: Habits, Journal, Focus, AI, Achievements |
| **CelebrationOverlay** | Full-screen particle animation on goal completion, badge earn, or level up |
| **MoodCalendar** | Calendar view with mood emoji per day in the Journal screen |
| **SyncStatusIndicator** | Top bar icon showing sync state (spinning/check/cloud-off/warning) |
| **OfflineBanner** | Non-blocking banner when device has no network |
| **GoalHistoryModal** | Bottom sheet showing full field change history for a goal |
