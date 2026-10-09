#!/usr/bin/env python3
"""Read-only copy of Supabase public tables (+ auth.users) into SQL files for D1.

  python3 scripts/copy-from-supabase.py [--since ISO] [--tables a,b]
  then: for f in scripts/out/*.sql; do wrangler d1 execute DB --remote --file $f; done

Idempotent (INSERT OR REPLACE by primary key). Only SELECTs are sent to Supabase.
"""
import json, sys, pathlib, argparse
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _supa import query, iso, lit

root = pathlib.Path(__file__).parent
out = root / "out"; out.mkdir(exist_ok=True)
ap = argparse.ArgumentParser(); ap.add_argument("--since"); ap.add_argument("--tables")
a = ap.parse_args()
schema = {t["table_name"]: t for t in json.load(open(root / "pg-schema.json"))}
names = a.tables.split(",") if a.tables else json.load(open(root / "tables.json"))
PAGE = 500

for name in names:
    cols = schema[name]["cols"]
    types = {c["n"]: c["u"] for c in cols}
    has_upd = "updated_at" in types
    where = f"where updated_at > '{a.since}'" if (a.since and has_upd) else ""
    lines, n = [], 0
    while True:
        rows = query(f'select * from public."{name}" {where} order by 1,2 limit {PAGE} offset {n}'
                     if len(cols) > 1 else f'select * from public."{name}" limit {PAGE} offset {n}')
        for r in rows:
            vals = []
            for c in cols:
                v = r.get(c["n"])
                u = c["u"]
                if v is not None and u == "timestamptz": v = iso(v)
                elif u == "bool" and v is not None: v = 1 if v else 0
                elif u == "jsonb" and v is not None: v = json.dumps(v, ensure_ascii=False, separators=(",", ":"))
                vals.append(lit(v))
            lines.append(f'INSERT OR REPLACE INTO "{name}" (' + ",".join(f'"{c["n"]}"' for c in cols)
                         + ") VALUES (" + ",".join(vals) + ");")
        n += len(rows)
        if len(rows) < PAGE: break
    (out / f"{name}.sql").write_text("\n".join(lines) + ("\n" if lines else ""))
    print(f"{name}: {n} rows")

if not a.tables or "legacy_auth_users" in a.tables:
    rows = query("""select id, email, is_anonymous, email_confirmed_at, created_at, last_sign_in_at,
        raw_user_meta_data->>'display_name' dn, raw_app_meta_data->>'provider' prov, encrypted_password
        from auth.users order by created_at""")
    lines = []
    for r in rows:
        v = [r["id"], r["email"], 1 if r["is_anonymous"] else 0, iso(r["email_confirmed_at"]), iso(r["created_at"]),
             iso(r["last_sign_in_at"]), r["dn"], r["prov"], r["encrypted_password"] or None]
        lines.append('INSERT OR REPLACE INTO "legacy_auth_users" VALUES (' + ",".join(lit(x) for x in v) + ");")
    (out / "zz_legacy_auth_users.sql").write_text("\n".join(lines) + "\n")
    print(f"legacy_auth_users: {len(rows)} rows")
