#!/usr/bin/env python3
"""Replay real source rows (scripts/out/*.sql, from copy-from-supabase.py) through the D1 gamification triggers
in a local SQLite and compare with what Postgres actually logged (gamification_log) and awarded (badges).

Local only: nothing is sent anywhere. Prints match / replay-only / prod-only per event type, xp mismatches among
matches, and badge differences. habit_checkin xp depends on the habit's streak *at the time*, so for that type only
the event set is compared.
"""
import sqlite3, pathlib, collections
root = pathlib.Path(__file__).parent; mig = root.parent / "migrations"; out = root / "out"

def load(db, tables):
    for t in tables:
        f = out / f"{t}.sql"
        if f.exists() and f.stat().st_size: db.executescript(f.read_text())

A = sqlite3.connect(":memory:"); A.row_factory = sqlite3.Row
A.executescript((mig / "0001_init.sql").read_text())
load(A, ["habits", "goals", "milestones", "habit_check_ins", "journal_entries", "focus_sessions",
         "beginner_objectives", "gamification_log", "badges", "challenges", "user_progress"])

B = sqlite3.connect(":memory:"); B.row_factory = sqlite3.Row
B.executescript((mig / "0001_init.sql").read_text()); B.executescript((mig / "0003_gamification.sql").read_text())
load(B, ["habits", "challenges"])

def insert(table, row, over=None):
    d = dict(row); d.update(over or {})
    B.execute(f'INSERT OR REPLACE INTO "{table}" ({",".join(map(chr34, d))}) VALUES ({",".join("?"*len(d))})', list(d.values()))
def chr34(c): return '"' + c + '"'
def update(table, id_, **kv):
    B.execute(f'UPDATE "{table}" SET ' + ",".join(f'"{k}"=?' for k in kv) + " WHERE id=?", [*kv.values(), id_])

# replay in created order; transitions go through UPDATE so the update triggers fire
for r in A.execute("select * from goals order by created_at"):
    insert("goals", r, {"status": "ACTIVE"})
    if r["status"] != "ACTIVE": update("goals", r["id"], status=r["status"])
for r in A.execute("select * from milestones order by created_at"):
    insert("milestones", r, {"is_completed": 0})
    if r["is_completed"]: update("milestones", r["id"], is_completed=1)
for r in A.execute("select * from journal_entries order by created_at"): insert("journal_entries", r)
for r in A.execute("select * from habit_check_ins order by date"): insert("habit_check_ins", r)
for r in A.execute("select * from focus_sessions order by created_at"):
    insert("focus_sessions", r, {"was_completed": 0})
    if r["was_completed"]: update("focus_sessions", r["id"], was_completed=1)
for r in A.execute("select * from beginner_objectives order by created_at"):
    insert("beginner_objectives", r, {"is_completed": 0})
    if r["is_completed"]: update("beginner_objectives", r["id"], is_completed=1)

key = lambda r: (r["user_id"], r["event_type"], r["source_id"])
prod = {key(r): r["xp_awarded"] for r in A.execute("select * from gamification_log")}
mine = {key(r): r["xp_awarded"] for r in B.execute("select * from gamification_log")}
by = collections.defaultdict(lambda: [0, 0, 0, 0])  # match, replay-only, prod-only, xp-mismatch
for k in prod.keys() | mine.keys():
    t = k[1]
    if k in prod and k in mine:
        by[t][0] += 1
        if t != "habit_checkin" and prod[k] != mine[k]: by[t][3] += 1
    elif k in mine: by[t][1] += 1
    else: by[t][2] += 1
print(f"{'event':22} match replay-only prod-only xp-mismatch")
for t, (m, ro, po, xm) in sorted(by.items()): print(f"{t:22} {m:5} {ro:11} {po:9} {xm:11}")
bad = [(t, v) for t, v in by.items() if v[3]]
pb = {(r["user_id"], r["badge_type"]) for r in A.execute("select * from badges where is_deleted=0")}
mb = {(r["user_id"], r["badge_type"]) for r in B.execute("select * from badges")}
print("badges: prod", len(pb), "replay", len(mb), "replay-only", len(mb - pb), "prod-only", len(pb - mb))
c = collections.Counter(t for _, t in (mb - pb)); print("  replay-only by type:", dict(c))
c = collections.Counter(t for _, t in (pb - mb)); print("  prod-only by type:", dict(c))
print("xp mismatches:", bad or "none")

if __import__("os").environ.get("DETAIL"):
    mm = collections.Counter()
    for k in prod.keys() & mine.keys():
        if k[1] != "habit_checkin" and prod[k] != mine[k]: mm[(k[1], prod[k], mine[k])] += 1
    print("mismatch (type, prod, mine): count"); [print(" ", k, v) for k, v in mm.most_common()]
