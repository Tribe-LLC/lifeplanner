-- Run this against your Supabase project to create and seed the builtin_coaches table.
-- Dashboard → SQL Editor → paste & run

CREATE TABLE IF NOT EXISTS builtin_coaches (
    id                    TEXT PRIMARY KEY,
    name                  TEXT NOT NULL,
    title                 TEXT NOT NULL,
    category              TEXT NOT NULL,
    emoji                 TEXT NOT NULL,
    image_url             TEXT,
    greeting              TEXT NOT NULL,
    bio                   TEXT NOT NULL,
    fun_fact              TEXT,
    specialties           JSONB DEFAULT '[]',
    personality           TEXT,
    city                  TEXT,
    timezone              TEXT DEFAULT 'UTC',
    country_flag          TEXT,
    avatar_bg_color       TEXT DEFAULT '#6366F1',
    avatar_accent_color   TEXT DEFAULT '#818CF8',
    avatar_icon_name      TEXT DEFAULT 'star',
    xp_to_unlock          INTEGER DEFAULT 0,
    is_default_unlocked   BOOLEAN DEFAULT TRUE,
    display_order         INTEGER DEFAULT 0,
    is_active             BOOLEAN DEFAULT TRUE,
    updated_at            TIMESTAMPTZ DEFAULT now()
);

-- Trigger: keep updated_at current on every row update
CREATE OR REPLACE FUNCTION set_builtin_coaches_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_builtin_coaches_updated_at ON builtin_coaches;
CREATE TRIGGER trg_builtin_coaches_updated_at
    BEFORE UPDATE ON builtin_coaches
    FOR EACH ROW EXECUTE FUNCTION set_builtin_coaches_updated_at();

-- RLS: everyone can read (app fetches without auth), only admin can write
ALTER TABLE builtin_coaches ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "builtin_coaches_public_read"  ON builtin_coaches;
DROP POLICY IF EXISTS "builtin_coaches_admin_write"  ON builtin_coaches;

CREATE POLICY "builtin_coaches_public_read"
    ON builtin_coaches FOR SELECT USING (true);

CREATE POLICY "builtin_coaches_admin_write"
    ON builtin_coaches FOR ALL
    USING ((auth.jwt() ->> 'email') = 'admin@lifeplanner.app');

-- ─── Seed: 6 built-in coaches ───────────────────────────────────────────────

INSERT INTO builtin_coaches (id, name, title, category, emoji, image_url, greeting, bio, fun_fact, specialties, personality, city, timezone, country_flag, avatar_bg_color, avatar_accent_color, avatar_icon_name, xp_to_unlock, is_default_unlocked, display_order)
VALUES
(
    'luna_general',
    'Luna',
    'Life Coach',
    'WELLBEING',
    '✨',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/luna.png',
    'Hey! I''m Luna, your personal life coach. What''s on your mind today?',
    'Your main guide on this journey. I see the big picture and help you connect all aspects of your life.',
    'I believe every small step counts toward your dreams!',
    '["Goal setting", "Motivation", "Life balance", "Personal growth"]',
    'warm, encouraging, holistic thinker',
    'Los Angeles',
    'America/Los_Angeles',
    '🇺🇸',
    '#6366F1',
    '#818CF8',
    'star',
    0,
    true,
    1
),
(
    'alex_career',
    'Alex',
    'Career Coach',
    'CAREER',
    '💼',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/alex.png',
    'Hi! I''m Alex, your career coach. Let''s work on your professional goals!',
    'Former headhunter turned coach. I know what it takes to climb the ladder.',
    'I''ve helped over 1000 people land their dream jobs!',
    '["Career planning", "Skills development", "Networking", "Job search"]',
    'professional, strategic, results-driven',
    'New York',
    'America/New_York',
    '🇺🇸',
    '#1976D2',
    '#42A5F5',
    'briefcase',
    100,
    true,
    2
),
(
    'morgan_finance',
    'Morgan',
    'Financial Coach',
    'MONEY',
    '💰',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/morgan.png',
    'Hello! I''m Morgan, your financial coach. Let''s build your wealth together!',
    'Numbers are my love language. Let me help you make cents of it all!',
    'I started saving from my first allowance at age 5!',
    '["Budgeting", "Saving", "Investing", "Financial goals"]',
    'analytical, practical, detail-oriented',
    'London',
    'Europe/London',
    '🇬🇧',
    '#388E3C',
    '#66BB6A',
    'dollar',
    150,
    true,
    3
),
(
    'kai_fitness',
    'Kai',
    'Fitness Coach',
    'BODY',
    '💪',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/kai.png',
    'Hey there! I''m Kai, your fitness coach. Ready to crush your health goals?',
    'Your body is your temple - let''s make it a masterpiece! No pain, all gain.',
    'I do 100 pushups every morning before sunrise!',
    '["Exercise", "Nutrition", "Weight management", "Energy"]',
    'energetic, motivating, action-oriented',
    'Sydney',
    'Australia/Sydney',
    '🇦🇺',
    '#E53935',
    '#EF5350',
    'fitness',
    200,
    true,
    4
),
(
    'sam_social',
    'Sam',
    'Social Coach',
    'PEOPLE',
    '🤝',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/sam.png',
    'Hi! I''m Sam, your social coach. Let''s strengthen your connections!',
    'Life is about connections. I''ll help you build meaningful relationships.',
    'I''ve never met a stranger - only friends I haven''t made yet!',
    '["Relationships", "Communication", "Networking", "Social skills"]',
    'friendly, empathetic, people-focused',
    'Paris',
    'Europe/Paris',
    '🇫🇷',
    '#7B1FA2',
    '#AB47BC',
    'people',
    250,
    true,
    5
),
(
    'river_wellness',
    'River',
    'Wellness Coach',
    'PURPOSE',
    '🧘',
    'https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/river.png',
    'Welcome! I''m River, your wellness coach. Let''s find your inner peace.',
    'Peace begins from within. Let me guide you to your calm center.',
    'I meditate for 2 hours daily and haven''t missed a day in 5 years!',
    '["Mindfulness", "Meditation", "Stress relief", "Self-care"]',
    'calm, thoughtful, mindful',
    'Tokyo',
    'Asia/Tokyo',
    '🇯🇵',
    '#00796B',
    '#26A69A',
    'spa',
    300,
    true,
    6
)
ON CONFLICT (id) DO NOTHING;
