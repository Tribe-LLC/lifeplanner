// check_and_award_badges for the daily-streak RPC. Rules live in the badge_rules table (migrations/0003).
const NOW = "strftime('%Y-%m-%dT%H:%M:%fZ','now')";
const UUID = "(lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||substr(lower(hex(randomblob(2))),2)||'-'||substr('89ab',abs(random())%4+1,1)||substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))))";
const COLS = "(id, user_id, badge_type, earned_at, is_new, updated_at, is_deleted, sync_version)";

export function awardBadgesStatement(db: D1Database, uid: string): D1PreparedStatement[] {
  const checks = "SELECT bt, req, kind FROM badge_rules";
  return [
    db.prepare(`INSERT INTO badges ${COLS} SELECT ${UUID}, p.user_id, 'FIRST_STEP', ${NOW}, 1, ${NOW}, 0, 0
      FROM user_progress p WHERE p.user_id = ?1 AND (SELECT COUNT(*) FROM gamification_log WHERE user_id = ?1 AND event_type = 'goal_created') >= 1 ON CONFLICT (user_id, badge_type) DO NOTHING`).bind(uid),
    db.prepare(`INSERT INTO badges ${COLS} SELECT ${UUID}, p.user_id, k.bt, ${NOW}, 1, ${NOW}, 0, 0
      FROM user_progress p, (${checks}) k WHERE p.user_id = ?1 AND (CASE k.kind WHEN 's' THEN p.current_streak WHEN 'g' THEN p.goals_completed WHEN 'h' THEN p.habits_completed ELSE p.journal_entries_count END) >= k.req ON CONFLICT (user_id, badge_type) DO NOTHING`).bind(uid),
  ];
}
