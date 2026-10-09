import json, subprocess, urllib.request, datetime, re
REF = "rkdggdfabwgukspylybu"

def _token():
    # Supabase CLI's own stored login. Read-only use, never printed.
    return subprocess.run(["security", "find-generic-password", "-s", "Supabase CLI", "-w"],
                          capture_output=True, text=True, check=True).stdout.strip()

def query(sql):
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{REF}/database/query",
        data=json.dumps({"query": sql}).encode(),
        headers={"Authorization": f"Bearer {_token()}", "Content-Type": "application/json",
                 "User-Agent": "lifeplanner-migration/0.1"})
    return json.load(urllib.request.urlopen(req, timeout=120))

def iso(v):
    """Normalise a Postgres timestamptz string to YYYY-MM-DDTHH:MM:SS.mmmZ (UTC)."""
    if v is None: return None
    s = v.replace(" ", "T")
    if s.endswith("+00"): s += ":00"
    s = s.replace("Z", "+00:00")
    m = re.match(r"^(.*?T\d\d:\d\d:\d\d)(\.\d+)?(.*)$", s)
    frac = ((m.group(2) or ".0")[1:] + "000000")[:6]
    d = datetime.datetime.fromisoformat(f"{m.group(1)}.{frac}{m.group(3)}")
    d = d.astimezone(datetime.timezone.utc)
    return d.strftime("%Y-%m-%dT%H:%M:%S.") + f"{d.microsecond // 1000:03d}Z"

def lit(v):
    if v is None: return "NULL"
    if isinstance(v, bool): return "1" if v else "0"
    if isinstance(v, (int, float)): return repr(v)
    if isinstance(v, (dict, list)): v = json.dumps(v, ensure_ascii=False, separators=(",", ":"))
    return "'" + str(v).replace("'", "''") + "'"
