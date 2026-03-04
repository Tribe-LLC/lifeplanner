# LifePlanner — Product Overview

## The Thesis

The human brain doesn't pursue goals with a single system — it runs parallel cognitive loops simultaneously. Evidence accumulation weighs whether a path is working. Reward circuits reinforce behaviors that pay off. The basal ganglia automates repeated actions into habits. Emotional signals flag what matters. Confidence monitoring tells you when to stay the course or pivot. Reflective learning extracts lessons from experience. And a mode-switching mechanism shifts you between habitual autopilot, deliberate goal pursuit, and open-ended exploration depending on the situation.

LifePlanner externalizes these cognitive loops into a structured digital system. Goals with milestones and dependency graphs handle evidence accumulation and planning. The habit engine with streaks and check-ins mirrors basal ganglia automation. Journaling with mood tracking captures emotional signals. AI coaching shifts users from autopilot into deliberate, goal-directed action. The focus timer manages attention allocation. Life balance assessments provide confidence monitoring across life domains. Gamification with XP, badges, and challenges drives reward reinforcement. Retrospectives and AI-generated reviews close the reflective learning loop. The result is a system that works the way your brain already does — just with better memory, no cognitive fatigue, and perfect consistency.

## What LifePlanner Is

LifePlanner is a cross-platform life management system built with Kotlin Multiplatform, shipping a single codebase to both Android and iOS via Compose Multiplatform. It is privacy-first — all AI processing routes through a server-side proxy with no client-side API keys, and users can choose between three AI providers (Gemini, ChatGPT, Grok). Data stays on-device by default with optional encrypted cloud sync. The project is fully open-source under the MIT license.

This is not a to-do app. It is a comprehensive system for managing goals, building habits, tracking emotions, getting AI coaching, maintaining focus, reviewing progress, and staying motivated — all in one place.

## Feature Map

### Goal Intelligence
*Externalizing evidence accumulation and planning*

Full goal lifecycle management with progress tracking (0–100%), milestones with due dates, and six dependency types (blocks, blocked-by, related, parent-of, child-of, supports) visualized in an interactive dependency graph. Goals are organized by seven life categories and three timeline horizons (short/mid/long-term). A complete field-change history tracks every modification. Pre-built templates with difficulty ratings and suggested milestones help users start quickly.

### Habit Engine
*Automating the basal ganglia loop*

Daily habit tracking with streak counting, frequency options, and check-in history. The system rewards consistency through streak-based XP multipliers and dedicated badges (Perfect Week, Perfect Month). Habits link to goals, so building routines directly feeds into larger objectives.

### Journal & Emotional Awareness
*Capturing emotional signals*

Journal entries with a five-point mood scale, tagging, and goal/habit linking. A mood calendar provides at-a-glance emotional trends. The Journal Wizard is a multi-step AI-assisted creation flow — select mood, link context, write notes, then optionally let AI expand or refine the entry. Built-in prompt categories (Daily Reflection, Goal Reflection, Mood Exploration, Weekly Review) help users who don't know where to start.

### AI Coaching System
*Switching from habitual to goal-directed behavior*

Seven built-in coach personas, each with a distinct specialty and communication style: Luna (life coaching), Alex (career), Morgan (finance), Kai (fitness), Sam (social), River (wellness), Jamie (family). Users can create custom coaches with eight personality presets. A unique Council mode lets multiple coaches respond to the same question in a group chat format. Coaches generate actionable suggestion cards — create a goal, start a habit, write a journal entry, check in on a habit, or walk through a multi-step questionnaire.

### Focus Timer
*Managing attention allocation*

Pomodoro-style timer with five duration presets (15–60 min) plus a +5 minute extension button. Six visual themes (Default, Rain, Forest, Fireplace, Ocean, Night Sky) and four ambient sounds (Rain, Forest, Lo-Fi, White Noise) create an immersive focus environment. Sessions tie to specific milestones, connecting focused work directly to goal progress. Partial XP is awarded for cancelled sessions over five minutes.

### Life Balance Assessment
*Confidence and certainty monitoring*

Eight life areas scored 0–100: Career, Financial, Physical, Social, Emotional, Spiritual, Family, and Personal Growth. Each area is rated on a five-point scale (Critical to Excellent) with trend tracking (Improving, Stable, Declining). AI generates personalized insights and prioritized recommendations based on balance data.

### Gamification & Motivation
*Reward and reinforcement learning*

XP system with 13 reward triggers (goal creation, completion, milestones, habit check-ins, journaling, focus sessions, daily check-ins, perfect days, streak bonuses). Nine level tiers from Novice to Life Master. 29 badges across seven categories (Streak, Goals, Habits, Journal, Category, Special, Focus). Time-boxed challenges at four tiers (daily, weekly, monthly, special) with escalating XP rewards. Full-screen celebration animations on goal completion, badge earning, and level-ups.

### Retrospectives & Reviews
*Closing the reflective learning loop*

AI-generated performance reviews at four intervals: weekly, monthly, quarterly, yearly. Each review includes highlights, data-driven insights with trend direction, actionable recommendations with priority levels, and comparative statistics against the previous period. The Retrospective screen provides a day-by-day activity snapshot — habit summary, journal entries, focus sessions, goal changes, badges earned, dominant mood, and total focus minutes for any chosen date.

### Smart Goal Generation
*Bootstrapping the planning process*

AI generates complete SMART goals from either free-text descriptions or six pre-built life scenarios (New Year New Me, Level Up Career, Wedding Ready, Financial Freedom, Health Transformation, Better Balance). Generated goals come with titles, descriptions, categories, timelines, and suggested milestones — ready to add with one tap.

## Technical Differentiation

- **Single codebase, two platforms:** Kotlin Multiplatform with Compose Multiplatform delivers native Android and iOS apps from one codebase
- **Privacy-first AI:** Server-side Supabase Edge Function proxy routes to three providers (Gemini, ChatGPT, Grok) — no API keys on device
- **Reactive data layer:** SQLDelight with coroutines-extensions provides reactive Flows from database to UI — mutations auto-propagate without manual refresh calls
- **Bidirectional sync:** 19-table sync engine with version counters, soft-delete, FK-dependency ordering, and debounced triggers
- **Clean architecture:** Domain/data/UI separation with Koin DI, repository pattern, and mapper layer
- **Offline-first:** Full functionality without network; sync when connectivity returns

## Architecture at a Glance

| Layer | Technology |
|-------|-----------|
| UI | Compose Multiplatform |
| State | Kotlin Flows + StateFlow in ViewModels |
| DI | Koin |
| Database | SQLDelight (SQLite) |
| Cloud | Supabase (Postgres + Edge Functions + Auth) |
| Auth | Firebase Auth + Supabase Auth |
| AI | Supabase Edge Function proxy → Gemini / OpenAI / Grok |
| Notifications | KMPNotifier |
| Widgets | Jetpack Glance (Android) |

## Current Status & Roadmap

LifePlanner is in active development. Core features (goals, habits, journal, AI coaching, focus timer, gamification, reviews, sync) are implemented and functional on both platforms. Current priorities include:

- App Store and Google Play public releases
- Expanded widget support (iOS WidgetKit)
- Collaborative goals and shared accountability
- Advanced analytics and trend visualization
- Wear OS / watchOS companion apps

## Platform Support

| Platform | Status |
|----------|--------|
| Android | Active development, Jetpack Glance widgets |
| iOS | Active development via Compose Multiplatform |