#!/usr/bin/env python3
"""Compare per-table row counts and max(updated_at) between Supabase (read-only) and the D1 copy."""
import json, subprocess, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _supa import query, iso
root = pathlib.Path(__file__).parent
schema = {t["table_name"]: t for t in json.load(open(root / "pg-schema.json"))}
names = json.load(open(root / "tables.json"))
db = sys.argv[1] if len(sys.argv) > 1 else "DB"

def d1(sql):
    for attempt in range(4):  # wrangler/D1 occasionally returns a transient error object
        r = subprocess.run(["npx", "wrangler", "d1", "execute", db, "--remote", "--json", "--command", sql],
                           capture_output=True, text=True, cwd=root.parent)
        try:
            return json.loads(r.stdout)[0]["results"]
        except (ValueError, KeyError, IndexError):
            pass
    raise SystemExit(f"D1 query failed after retries: {sql}\n{r.stdout[:300]}")

bad = 0
for n in names + ["legacy_auth_users"]:
    if n == "legacy_auth_users":
        pc = query("select count(*) c, max(created_at) m from auth.users")[0]
        dc = d1('select count(*) c, max(created_at) m from legacy_auth_users')[0]
        pm, dm = iso(pc["m"]), dc["m"]
    else:
        has = any(c["n"] == "updated_at" for c in schema[n]["cols"])
        col = "updated_at" if has else None
        pc = query(f'select count(*) c{", max(updated_at) m" if has else ""} from public."{n}"')[0]
        dc = d1(f'select count(*) c{", max(updated_at) m" if has else ""} from "{n}"')[0]
        pm, dm = (iso(pc.get("m")), dc.get("m")) if has else (None, None)
    ok = pc["c"] == dc["c"] and pm == dm
    if not ok: bad += 1
    print(("OK  " if ok else "DIFF"), n, pc["c"], dc["c"], pm or "", dm or "")
print("mismatches:", bad)
sys.exit(1 if bad else 0)
