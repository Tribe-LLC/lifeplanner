-- ============================================================
-- Life Planner – Supabase Cloud Sync Schema
-- ============================================================
-- Run this file once in the Supabase SQL Editor to bootstrap
-- all tables, RLS policies, triggers, and indexes.
-- PREREQUISITE: Enable the pgvector extension in Supabase Dashboard
-- → Database → Extensions → vector, or run:
--   CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA extensions;
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. Shared trigger function: auto-update updated_at & sync_version
-- ────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION update_sync_metadata()
RETURNS TRIGGER
SET search_path = public
AS $$
BEGIN
    NEW.updated_at   = now();
    NEW.sync_version = COALESCE(OLD.sync_version, 0) + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ────────────────────────────────────────────────────────────
-- 2. CREATE TABLE statements (19 tables)
-- ────────────────────────────────────────────────────────────

-- 2.1  users
CREATE TABLE users (
    id          TEXT        PRIMARY KEY,
    user_id     UUID        NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    firebase_uid TEXT       UNIQUE,
    email       TEXT,
    display_name TEXT,
    is_guest    BOOLEAN     NOT NULL DEFAULT FALSE,
    selected_symbol TEXT,
    priorities  JSONB,
    age_range   TEXT,
    profession  TEXT,
    relationship_status TEXT,
    mindset     TEXT,
    has_completed_onboarding BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at TIMESTAMPTZ,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.2  goals
CREATE TABLE goals (
    id              TEXT        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    category        TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    description     TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    timeline        TEXT        NOT NULL,
    due_date        TEXT        NOT NULL,
    progress        INTEGER     NOT NULL DEFAULT 0,
    notes           TEXT        NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completion_rate REAL        NOT NULL DEFAULT 0.0,
    is_archived     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.3  milestones
CREATE TABLE milestones (
    id           TEXT        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    goal_id      TEXT        NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    title        TEXT        NOT NULL,
    is_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    due_date     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.4  goal_history  (no FK to goals – history persists after goal deletion)
CREATE TABLE goal_history (
    id         TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    goal_id    TEXT        NOT NULL,
    field      TEXT        NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    changed_at TIMESTAMPTZ NOT NULL,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.5  user_progress  (singleton per user → user_id is PK)
CREATE TABLE user_progress (
    user_id              UUID        PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    current_streak       INTEGER     NOT NULL DEFAULT 0,
    last_check_in_date   TEXT,
    total_xp             INTEGER     NOT NULL DEFAULT 0,
    current_level        INTEGER     NOT NULL DEFAULT 1,
    goals_completed      INTEGER     NOT NULL DEFAULT 0,
    habits_completed     INTEGER     NOT NULL DEFAULT 0,
    journal_entries_count INTEGER    NOT NULL DEFAULT 0,
    longest_streak       INTEGER     NOT NULL DEFAULT 0,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.6  habits
CREATE TABLE habits (
    id                TEXT        PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title             TEXT        NOT NULL,
    description       TEXT        NOT NULL DEFAULT '',
    category          TEXT        NOT NULL,
    frequency         TEXT        NOT NULL DEFAULT 'DAILY',
    target_count      INTEGER     NOT NULL DEFAULT 1,
    current_streak    INTEGER     NOT NULL DEFAULT 0,
    longest_streak    INTEGER     NOT NULL DEFAULT 0,
    total_completions INTEGER     NOT NULL DEFAULT 0,
    last_completed_date TEXT,
    linked_goal_id    TEXT        REFERENCES goals(id) ON DELETE SET NULL,
    correlation_score REAL        NOT NULL DEFAULT 0.0,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    reminder_time     TEXT,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.7  habit_check_ins
CREATE TABLE habit_check_ins (
    id        TEXT        PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    habit_id  TEXT        NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    date      TEXT        NOT NULL,
    completed BOOLEAN     NOT NULL DEFAULT TRUE,
    notes     TEXT        NOT NULL DEFAULT '',
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (user_id, habit_id, date)
);

-- 2.8  journal_entries
CREATE TABLE journal_entries (
    id               TEXT        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title            TEXT        NOT NULL,
    content          TEXT        NOT NULL,
    mood             TEXT        NOT NULL DEFAULT 'NEUTRAL',
    linked_goal_id   TEXT        REFERENCES goals(id) ON DELETE SET NULL,
    linked_habit_id  TEXT        REFERENCES habits(id) ON DELETE SET NULL,
    prompt_used      TEXT,
    tags             JSONB       NOT NULL DEFAULT '[]'::jsonb,
    date             TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    entry_updated_at TIMESTAMPTZ,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.9  badges
CREATE TABLE badges (
    id         TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    badge_type TEXT        NOT NULL,
    earned_at  TIMESTAMPTZ NOT NULL,
    is_new     BOOLEAN     NOT NULL DEFAULT TRUE,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.10 challenges
CREATE TABLE challenges (
    id               TEXT        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    challenge_type   TEXT        NOT NULL,
    start_date       TEXT        NOT NULL,
    end_date         TEXT        NOT NULL,
    current_progress INTEGER     NOT NULL DEFAULT 0,
    target_progress  INTEGER     NOT NULL,
    is_completed     BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at     TIMESTAMPTZ,
    xp_earned        INTEGER     NOT NULL DEFAULT 0,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.11 goal_dependencies
CREATE TABLE goal_dependencies (
    id              TEXT        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    source_goal_id  TEXT        NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    target_goal_id  TEXT        NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    dependency_type TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.12 chat_sessions
CREATE TABLE chat_sessions (
    id              TEXT        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title           TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary         TEXT,
    coach_id        TEXT        NOT NULL DEFAULT 'luna_general',
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.13 chat_messages
CREATE TABLE chat_messages (
    id              TEXT        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    session_id      TEXT        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    content         TEXT        NOT NULL,
    role            TEXT        NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL,
    related_goal_id TEXT,
    metadata        JSONB,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.14 review_reports
CREATE TABLE review_reports (
    id                   TEXT        PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type                 TEXT        NOT NULL,
    period_start         TEXT        NOT NULL,
    period_end           TEXT        NOT NULL,
    generated_at         TIMESTAMPTZ NOT NULL,
    summary              TEXT        NOT NULL,
    highlights_json      JSONB       NOT NULL,
    insights_json        JSONB       NOT NULL,
    recommendations_json JSONB       NOT NULL,
    stats_json           JSONB       NOT NULL,
    feedback_rating      TEXT,
    feedback_comment     TEXT,
    feedback_at          TIMESTAMPTZ,
    is_read              BOOLEAN     NOT NULL DEFAULT FALSE,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.15 reminders
CREATE TABLE reminders (
    id                TEXT        PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title             TEXT        NOT NULL,
    message           TEXT        NOT NULL,
    type              TEXT        NOT NULL,
    frequency         TEXT        NOT NULL,
    scheduled_time    TEXT        NOT NULL,
    scheduled_days    TEXT        NOT NULL DEFAULT '',
    linked_goal_id    TEXT        REFERENCES goals(id) ON DELETE SET NULL,
    linked_habit_id   TEXT        REFERENCES habits(id) ON DELETE SET NULL,
    is_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    is_smart_timing   BOOLEAN     NOT NULL DEFAULT FALSE,
    last_triggered_at TIMESTAMPTZ,
    snoozed_until     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    reminder_updated_at TIMESTAMPTZ,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.16 custom_coaches
CREATE TABLE custom_coaches (
    id                   TEXT        PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name                 TEXT        NOT NULL,
    icon                 TEXT        NOT NULL,
    icon_background_color TEXT       NOT NULL DEFAULT '#6366F1',
    icon_accent_color    TEXT        NOT NULL DEFAULT '#818CF8',
    system_prompt        TEXT        NOT NULL,
    characteristics      JSONB       NOT NULL DEFAULT '[]'::jsonb,
    is_from_template     BOOLEAN     NOT NULL DEFAULT FALSE,
    template_id          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    coach_updated_at     TIMESTAMPTZ,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.17 coach_groups
CREATE TABLE coach_groups (
    id               TEXT        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    icon             TEXT        NOT NULL,
    description      TEXT        NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    group_updated_at TIMESTAMPTZ,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.18 coach_group_members
CREATE TABLE coach_group_members (
    id            TEXT        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    group_id      TEXT        NOT NULL REFERENCES coach_groups(id) ON DELETE CASCADE,
    coach_type    TEXT        NOT NULL,
    coach_id      TEXT        NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.19 focus_sessions
CREATE TABLE focus_sessions (
    id                       TEXT        PRIMARY KEY,
    user_id                  UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    goal_id                  TEXT        REFERENCES goals(id) ON DELETE SET NULL,
    milestone_id             TEXT        REFERENCES milestones(id) ON DELETE SET NULL,
    planned_duration_minutes INTEGER     NOT NULL,
    actual_duration_seconds  INTEGER     NOT NULL DEFAULT 0,
    was_completed            BOOLEAN     NOT NULL DEFAULT FALSE,
    xp_earned                INTEGER     NOT NULL DEFAULT 0,
    started_at               TIMESTAMPTZ NOT NULL,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    mood                     TEXT,
    ambient_sound            TEXT,
    focus_theme              TEXT,
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0
);

-- 2.20 coach_persona_overrides
CREATE TABLE coach_persona_overrides (
    coach_id     TEXT        NOT NULL,
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    user_persona TEXT        NOT NULL DEFAULT '',
    -- sync metadata
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_version BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (coach_id, user_id)
);

-- 2.21 content_embeddings (RAG vector store for AI proxy)
CREATE TABLE content_embeddings (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    source_table TEXT        NOT NULL,
    source_id    TEXT        NOT NULL,
    content      TEXT        NOT NULL,
    embedding    vector(768) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, source_table, source_id)
);

-- 2.22 ai_usage_logs (fire-and-forget usage tracking)
CREATE TABLE ai_usage_logs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL DEFAULT auth.uid() REFERENCES auth.users(id) ON DELETE CASCADE,
    provider      TEXT        NOT NULL,
    model         TEXT        NOT NULL,
    input_tokens  INTEGER     NOT NULL DEFAULT 0,
    output_tokens INTEGER     NOT NULL DEFAULT 0,
    request_type  TEXT        NOT NULL DEFAULT 'chat',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Vector similarity search RPC used by ai-proxy
CREATE OR REPLACE FUNCTION match_user_content(
    query_embedding vector(768),
    match_count     INT DEFAULT 10,
    similarity_threshold FLOAT DEFAULT 0.3
)
RETURNS TABLE (
    id            UUID,
    source_table  TEXT,
    source_id     TEXT,
    content       TEXT,
    similarity    FLOAT
)
SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    SELECT
        ce.id,
        ce.source_table,
        ce.source_id,
        ce.content,
        1 - (ce.embedding <=> query_embedding) AS similarity
    FROM content_embeddings ce
    WHERE ce.user_id = auth.uid()
      AND 1 - (ce.embedding <=> query_embedding) > similarity_threshold
    ORDER BY ce.embedding <=> query_embedding
    LIMIT match_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ────────────────────────────────────────────────────────────
-- 3. Triggers – auto-update sync metadata on every UPDATE
-- ────────────────────────────────────────────────────────────

CREATE TRIGGER trg_users_sync           BEFORE UPDATE ON users              FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_goals_sync           BEFORE UPDATE ON goals              FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_milestones_sync      BEFORE UPDATE ON milestones         FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_goal_history_sync    BEFORE UPDATE ON goal_history       FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_user_progress_sync   BEFORE UPDATE ON user_progress      FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_habits_sync          BEFORE UPDATE ON habits             FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_habit_check_ins_sync BEFORE UPDATE ON habit_check_ins    FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_journal_entries_sync BEFORE UPDATE ON journal_entries     FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_badges_sync          BEFORE UPDATE ON badges             FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_challenges_sync      BEFORE UPDATE ON challenges         FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_goal_deps_sync       BEFORE UPDATE ON goal_dependencies  FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_chat_sessions_sync   BEFORE UPDATE ON chat_sessions      FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_chat_messages_sync   BEFORE UPDATE ON chat_messages      FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_review_reports_sync  BEFORE UPDATE ON review_reports     FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_reminders_sync       BEFORE UPDATE ON reminders          FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_custom_coaches_sync  BEFORE UPDATE ON custom_coaches     FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_coach_groups_sync    BEFORE UPDATE ON coach_groups       FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_coach_grp_mbrs_sync  BEFORE UPDATE ON coach_group_members FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_focus_sessions_sync  BEFORE UPDATE ON focus_sessions     FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();
CREATE TRIGGER trg_coach_persona_sync   BEFORE UPDATE ON coach_persona_overrides FOR EACH ROW EXECUTE FUNCTION update_sync_metadata();

-- ────────────────────────────────────────────────────────────
-- 4. Indexes
-- ────────────────────────────────────────────────────────────

-- Composite indexes with user_id first for RLS performance
-- + updated_at indexes for sync pull queries

-- users
CREATE INDEX idx_users_updated ON users(user_id, updated_at);

-- goals
CREATE INDEX idx_goals_user         ON goals(user_id);
CREATE INDEX idx_goals_user_status  ON goals(user_id, status);
CREATE INDEX idx_goals_updated      ON goals(user_id, updated_at);

-- milestones
CREATE INDEX idx_milestones_user    ON milestones(user_id);
CREATE INDEX idx_milestones_goal    ON milestones(user_id, goal_id);
CREATE INDEX idx_milestones_updated ON milestones(user_id, updated_at);

-- goal_history
CREATE INDEX idx_goal_history_updated  ON goal_history(user_id, updated_at);

-- user_progress  (PK is user_id, so only sync index needed)
CREATE INDEX idx_user_progress_updated ON user_progress(user_id, updated_at);

-- habits
CREATE INDEX idx_habits_user          ON habits(user_id);
CREATE INDEX idx_habits_updated       ON habits(user_id, updated_at);

-- habit_check_ins
CREATE INDEX idx_checkins_user        ON habit_check_ins(user_id);
CREATE INDEX idx_checkins_user_habit  ON habit_check_ins(user_id, habit_id);
CREATE INDEX idx_checkins_updated     ON habit_check_ins(user_id, updated_at);

-- journal_entries
CREATE INDEX idx_journal_user_date    ON journal_entries(user_id, date);
CREATE INDEX idx_journal_user_goal    ON journal_entries(user_id, linked_goal_id);
CREATE INDEX idx_journal_user_habit   ON journal_entries(user_id, linked_habit_id);
CREATE INDEX idx_journal_updated      ON journal_entries(user_id, updated_at);

-- badges
CREATE INDEX idx_badges_user          ON badges(user_id);
CREATE INDEX idx_badges_user_type     ON badges(user_id, badge_type);
CREATE INDEX idx_badges_updated       ON badges(user_id, updated_at);

-- challenges
CREATE INDEX idx_challenges_user_type     ON challenges(user_id, challenge_type);
CREATE INDEX idx_challenges_user_completed ON challenges(user_id, is_completed);
CREATE INDEX idx_challenges_updated       ON challenges(user_id, updated_at);

-- goal_dependencies
CREATE INDEX idx_goal_deps_source     ON goal_dependencies(user_id, source_goal_id);
CREATE INDEX idx_goal_deps_target     ON goal_dependencies(user_id, target_goal_id);
CREATE INDEX idx_goal_deps_updated    ON goal_dependencies(user_id, updated_at);

-- chat_sessions
CREATE INDEX idx_chat_sessions_updated    ON chat_sessions(user_id, updated_at);

-- chat_messages
CREATE INDEX idx_chat_messages_user       ON chat_messages(user_id);
CREATE INDEX idx_chat_messages_session    ON chat_messages(user_id, session_id);
CREATE INDEX idx_chat_messages_updated    ON chat_messages(user_id, updated_at);

-- review_reports
CREATE INDEX idx_reviews_updated          ON review_reports(user_id, updated_at);

-- reminders
CREATE INDEX idx_reminders_user_goal      ON reminders(user_id, linked_goal_id);
CREATE INDEX idx_reminders_user_habit     ON reminders(user_id, linked_habit_id);
CREATE INDEX idx_reminders_updated        ON reminders(user_id, updated_at);

-- custom_coaches
CREATE INDEX idx_coaches_updated          ON custom_coaches(user_id, updated_at);

-- coach_groups
CREATE INDEX idx_coach_groups_updated     ON coach_groups(user_id, updated_at);

-- coach_group_members
CREATE INDEX idx_coach_grp_mbrs_updated   ON coach_group_members(user_id, updated_at);

-- focus_sessions
CREATE INDEX idx_focus_user_goal          ON focus_sessions(user_id, goal_id);
CREATE INDEX idx_focus_user_milestone     ON focus_sessions(user_id, milestone_id);
CREATE INDEX idx_focus_user_started       ON focus_sessions(user_id, started_at);
CREATE INDEX idx_focus_updated            ON focus_sessions(user_id, updated_at);

-- coach_persona_overrides
CREATE INDEX idx_coach_persona_user      ON coach_persona_overrides(user_id);
CREATE INDEX idx_coach_persona_updated   ON coach_persona_overrides(user_id, updated_at);

-- content_embeddings
CREATE INDEX idx_embeddings_user         ON content_embeddings(user_id);
CREATE INDEX idx_embeddings_source       ON content_embeddings(user_id, source_table, source_id);

-- ai_usage_logs
CREATE INDEX idx_ai_usage_user           ON ai_usage_logs(user_id);
CREATE INDEX idx_ai_usage_created        ON ai_usage_logs(user_id, created_at);

-- FK indexes for cascade performance
CREATE INDEX idx_chat_messages_session_fk ON chat_messages(session_id);
CREATE INDEX idx_coach_grp_mbrs_group_fk ON coach_group_members(group_id);
CREATE INDEX idx_coach_persona_user_fk   ON coach_persona_overrides(user_id);
CREATE INDEX idx_focus_goal_fk           ON focus_sessions(goal_id);
CREATE INDEX idx_focus_milestone_fk      ON focus_sessions(milestone_id);
CREATE INDEX idx_goal_deps_source_fk     ON goal_dependencies(source_goal_id);
CREATE INDEX idx_goal_deps_target_fk     ON goal_dependencies(target_goal_id);
CREATE INDEX idx_checkins_habit_fk       ON habit_check_ins(habit_id);
CREATE INDEX idx_habits_goal_fk          ON habits(linked_goal_id);
CREATE INDEX idx_journal_goal_fk         ON journal_entries(linked_goal_id);
CREATE INDEX idx_journal_habit_fk        ON journal_entries(linked_habit_id);
CREATE INDEX idx_milestones_goal_fk      ON milestones(goal_id);
CREATE INDEX idx_reminders_goal_fk       ON reminders(linked_goal_id);
CREATE INDEX idx_reminders_habit_fk      ON reminders(linked_habit_id);

-- ────────────────────────────────────────────────────────────
-- 5. Enable Row Level Security on every table
-- ────────────────────────────────────────────────────────────

ALTER TABLE users               ENABLE ROW LEVEL SECURITY;
ALTER TABLE goals               ENABLE ROW LEVEL SECURITY;
ALTER TABLE milestones          ENABLE ROW LEVEL SECURITY;
ALTER TABLE goal_history        ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_progress       ENABLE ROW LEVEL SECURITY;
ALTER TABLE habits              ENABLE ROW LEVEL SECURITY;
ALTER TABLE habit_check_ins     ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entries     ENABLE ROW LEVEL SECURITY;
ALTER TABLE badges              ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenges          ENABLE ROW LEVEL SECURITY;
ALTER TABLE goal_dependencies   ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_sessions       ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages       ENABLE ROW LEVEL SECURITY;
ALTER TABLE review_reports      ENABLE ROW LEVEL SECURITY;
ALTER TABLE reminders           ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_coaches      ENABLE ROW LEVEL SECURITY;
ALTER TABLE coach_groups        ENABLE ROW LEVEL SECURITY;
ALTER TABLE coach_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE focus_sessions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE coach_persona_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_embeddings      ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_usage_logs           ENABLE ROW LEVEL SECURITY;

-- ────────────────────────────────────────────────────────────
-- 6. RLS Policies
-- ────────────────────────────────────────────────────────────

-- Helper: each policy pattern is:
--   SELECT  → (select auth.uid()) = user_id
--   INSERT  → (select auth.uid()) = user_id
--   UPDATE  → (select auth.uid()) = user_id
--   DELETE  → (select auth.uid()) = user_id

-- 6.1  users
CREATE POLICY users_select ON users FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY users_insert ON users FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY users_update ON users FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY users_delete ON users FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.2  goals
CREATE POLICY goals_select ON goals FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY goals_insert ON goals FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goals_update ON goals FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goals_delete ON goals FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.3  milestones
CREATE POLICY milestones_select ON milestones FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY milestones_insert ON milestones FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY milestones_update ON milestones FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY milestones_delete ON milestones FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.4  goal_history
CREATE POLICY goal_history_select ON goal_history FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY goal_history_insert ON goal_history FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goal_history_update ON goal_history FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goal_history_delete ON goal_history FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.5  user_progress
CREATE POLICY user_progress_select ON user_progress FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY user_progress_insert ON user_progress FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY user_progress_update ON user_progress FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY user_progress_delete ON user_progress FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.6  habits
CREATE POLICY habits_select ON habits FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY habits_insert ON habits FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY habits_update ON habits FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY habits_delete ON habits FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.7  habit_check_ins
CREATE POLICY habit_check_ins_select ON habit_check_ins FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY habit_check_ins_insert ON habit_check_ins FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY habit_check_ins_update ON habit_check_ins FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY habit_check_ins_delete ON habit_check_ins FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.8  journal_entries
CREATE POLICY journal_entries_select ON journal_entries FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY journal_entries_insert ON journal_entries FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY journal_entries_update ON journal_entries FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY journal_entries_delete ON journal_entries FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.9  badges
CREATE POLICY badges_select ON badges FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY badges_insert ON badges FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY badges_update ON badges FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY badges_delete ON badges FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.10 challenges
CREATE POLICY challenges_select ON challenges FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY challenges_insert ON challenges FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY challenges_update ON challenges FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY challenges_delete ON challenges FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.11 goal_dependencies
CREATE POLICY goal_dependencies_select ON goal_dependencies FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY goal_dependencies_insert ON goal_dependencies FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goal_dependencies_update ON goal_dependencies FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY goal_dependencies_delete ON goal_dependencies FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.12 chat_sessions
CREATE POLICY chat_sessions_select ON chat_sessions FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY chat_sessions_insert ON chat_sessions FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY chat_sessions_update ON chat_sessions FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY chat_sessions_delete ON chat_sessions FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.13 chat_messages
CREATE POLICY chat_messages_select ON chat_messages FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY chat_messages_insert ON chat_messages FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY chat_messages_update ON chat_messages FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY chat_messages_delete ON chat_messages FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.14 review_reports
CREATE POLICY review_reports_select ON review_reports FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY review_reports_insert ON review_reports FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY review_reports_update ON review_reports FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY review_reports_delete ON review_reports FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.15 reminders
CREATE POLICY reminders_select ON reminders FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY reminders_insert ON reminders FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY reminders_update ON reminders FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY reminders_delete ON reminders FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.16 custom_coaches
CREATE POLICY custom_coaches_select ON custom_coaches FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY custom_coaches_insert ON custom_coaches FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY custom_coaches_update ON custom_coaches FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY custom_coaches_delete ON custom_coaches FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.17 coach_groups
CREATE POLICY coach_groups_select ON coach_groups FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY coach_groups_insert ON coach_groups FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_groups_update ON coach_groups FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_groups_delete ON coach_groups FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.18 coach_group_members
CREATE POLICY coach_group_members_select ON coach_group_members FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY coach_group_members_insert ON coach_group_members FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_group_members_update ON coach_group_members FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_group_members_delete ON coach_group_members FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.19 focus_sessions
CREATE POLICY focus_sessions_select ON focus_sessions FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY focus_sessions_insert ON focus_sessions FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY focus_sessions_update ON focus_sessions FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY focus_sessions_delete ON focus_sessions FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.20 coach_persona_overrides
CREATE POLICY coach_persona_overrides_select ON coach_persona_overrides FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY coach_persona_overrides_insert ON coach_persona_overrides FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_persona_overrides_update ON coach_persona_overrides FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY coach_persona_overrides_delete ON coach_persona_overrides FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.21 content_embeddings
CREATE POLICY content_embeddings_select ON content_embeddings FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY content_embeddings_insert ON content_embeddings FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY content_embeddings_update ON content_embeddings FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id) WITH CHECK ((select auth.uid()) = user_id);
CREATE POLICY content_embeddings_delete ON content_embeddings FOR DELETE TO authenticated USING ((select auth.uid()) = user_id);

-- 6.22 ai_usage_logs (insert-only for clients; select own logs)
CREATE POLICY ai_usage_logs_select ON ai_usage_logs FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
CREATE POLICY ai_usage_logs_insert ON ai_usage_logs FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);

-- ────────────────────────────────────────────────────────────
-- 7. Tombstone cleanup – hard-delete soft-deleted rows > 30 days
-- ────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION cleanup_tombstones()
RETURNS void
SET search_path = public
AS $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY[
            'users', 'goals', 'milestones', 'goal_history', 'user_progress',
            'habits', 'habit_check_ins', 'journal_entries', 'badges', 'challenges',
            'goal_dependencies', 'chat_sessions', 'chat_messages', 'review_reports',
            'reminders', 'custom_coaches', 'coach_groups', 'coach_group_members',
            'focus_sessions', 'coach_persona_overrides'
        ])
    LOOP
        EXECUTE format(
            'DELETE FROM %I WHERE is_deleted = TRUE AND updated_at < now() - interval ''30 days''',
            tbl
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Schedule tombstone cleanup daily at 03:00 UTC via pg_cron.
-- PREREQUISITE: Enable pg_cron in Supabase Dashboard → Database → Extensions,
-- then run the following separately:
--
--   SELECT cron.schedule(
--       'cleanup-tombstones',
--       '0 3 * * *',
--       $$SELECT cleanup_tombstones()$$
--   );
