# LifePlanner

**A cross-platform life management system** — goals, habits, journaling, AI coaching, focus timer, gamification, and more. Built with Kotlin Multiplatform for Android and iOS.

![screenshot](preview.png)

---

## Features

### Goals & Planning
- Create, edit, and track goals with progress (0–100%) and milestones
- Six dependency types with interactive dependency graph visualization
- Goal templates with suggested milestones and difficulty ratings
- AI-powered SMART goal generation from text or life scenarios
- Full field-change history for every goal
- Organize by category (Career, Financial, Physical, Social, Emotional, Spiritual, Family) and timeline

### Habits
- Daily habit tracking with streaks and frequency options
- Habit-to-goal linking
- Streak-based XP multipliers and badges (Perfect Week, Perfect Month)

### Journal
- Entries with mood tracking (5-point scale), tags, and goal/habit linking
- Mood calendar for at-a-glance emotional trends
- AI-assisted Journal Wizard (multi-step creation flow)
- Built-in prompt categories: Daily Reflection, Goal Reflection, Mood Exploration, Weekly Review

### AI Coaching
- 7 built-in coach personas (Luna, Alex, Morgan, Kai, Sam, River, Jamie)
- Custom coach creation with 8 personality presets
- Council mode — multiple coaches respond together
- Actionable suggestion cards (create goal, start habit, journal entry, check-in, questionnaire)
- 3 AI providers: Gemini, ChatGPT, Grok — user-selectable

### Focus Timer
- Pomodoro-style timer (15/25/30/45/60 min + extendable)
- 6 visual themes (Default, Rain, Forest, Fireplace, Ocean, Night Sky)
- 4 ambient sounds (Rain, Forest, Lo-Fi, White Noise)
- Sessions tied to milestones for direct goal progress

### Life Balance
- 8 life areas scored 0–100 with trend tracking
- AI-generated insights and prioritized recommendations

### Gamification
- XP, levels (Novice → Life Master), and streak bonuses
- 29 badges across 7 categories
- Daily, weekly, monthly, and special challenges
- Celebration animations on achievements

### Reviews & Retrospectives
- AI-generated periodic reviews (weekly/monthly/quarterly/yearly)
- Day-by-day retrospective snapshots
- Highlights, insights, recommendations, and comparative stats

### Data & Privacy
- Offline-first with optional bidirectional cloud sync (19 tables)
- No client-side API keys — AI routes through server-side proxy
- Full data export/import as JSON backup
- Firebase + Supabase authentication with guest mode

---

## Download

| Platform | Link |
|----------|------|
| Android | [Google Play – coming soon](#) |
| iOS | [App Store – coming soon](#) |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin Multiplatform |
| UI | Compose Multiplatform |
| Database | SQLDelight (reactive Flows) |
| DI | Koin |
| Cloud | Supabase (Postgres, Edge Functions, Auth) |
| Auth | Firebase Auth + Supabase Auth |
| AI Proxy | Supabase Edge Function → Gemini / OpenAI / Grok |
| Notifications | KMPNotifier |
| Widgets | Jetpack Glance (Android) |
| Architecture | Clean Architecture (Domain / Data / UI) |

---

## How to Run

```bash
git clone https://github.com/kamranmammadov/lifeplanner.git
cd lifeplanner
./gradlew build
```

Run on Android:

```bash
./gradlew :composeApp:installDebug
```

---

## Project Structure

```
composeApp/
  src/
    commonMain/     Shared logic, UI, domain, data, DI
    androidMain/    Android-specific implementations
    iosMain/        iOS-specific implementations
iosApp/             iOS app entry point (SwiftUI host)
supabase/
  functions/        Edge Functions (ai-proxy, etc.)
  migrations/       Database migrations
```

---

## Documentation

- [Terminology](TERMINOLOGY.md) — full glossary of features, domain terms, and shorthand
- [Implementation Plan](IMPLEMENTATION_PLAN.md) — development phases and progress
- [Security](SECURITY.md) — security practices and vulnerability reporting

---

## Contributing

We welcome contributions!

1. Fork the repo
2. Create a feature branch
3. Commit your changes
4. Open a pull request

See [CONTRIBUTING.md](CONTRIBUTING.md) for full details.

---

## License

This project is licensed under the [MIT License](LICENSE).

---

Developed by [Kamran Mammadov](https://github.com/kamranmammadov)
