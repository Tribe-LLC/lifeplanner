import { TABLES, type Kind } from "./tables";

/** ISO-8601 UTC with millisecond precision: the one timestamp format stored in D1. */
export function nowIso(): string {
  return new Date().toISOString();
}

export function normTs(v: unknown): string | null {
  if (v === null || v === undefined) return null;
  const d = new Date(String(v));
  if (Number.isNaN(d.getTime())) throw new BadRequest(`invalid timestamp: ${String(v)}`);
  return d.toISOString();
}

export class BadRequest extends Error {}

/** JSON value -> D1 parameter. */
export function toDb(kind: Kind, v: unknown): string | number | null {
  if (v === null || v === undefined) return null;
  switch (kind) {
    case "b": return v === true || v === 1 || v === "true" ? 1 : 0;
    case "j": case "a": return JSON.stringify(v);
    case "t": return normTs(v);
    case "n": {
      const n = typeof v === "number" ? v : Number(v);
      if (!Number.isFinite(n)) throw new BadRequest(`invalid number: ${String(v)}`);
      return n;
    }
    default: return typeof v === "string" ? v : String(v);
  }
}

/** D1 row -> JSON value shaped like the PostgREST response the app already decodes. */
export function fromDb(table: string, row: Record<string, unknown>): Record<string, unknown> {
  const cols = TABLES[table].cols;
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    const kind = cols[k];
    if (v === null || v === undefined) out[k] = null;
    else if (kind === "b") out[k] = v === 1 || v === true;
    else if (kind === "j" || kind === "a") out[k] = JSON.parse(String(v));
    else out[k] = v;
  }
  return out;
}

const q = (id: string) => `"${id.replace(/"/g, '""')}"`;

/**
 * Upsert statement for one row, mirroring PostgREST `Prefer: resolution=merge-duplicates` plus the Postgres
 * `update_sync_metadata` BEFORE UPDATE trigger: a conflict overwrites the row, stamps updated_at with the server
 * clock and bumps sync_version; a fresh insert keeps the client's values.
 */
export function upsertStatement(db: D1Database, table: string, row: Record<string, unknown>, userId: string | null): D1PreparedStatement {
  const def = TABLES[table];
  const keys = Object.keys(row);
  for (const k of keys) if (!(k in def.cols)) throw new BadRequest(`unknown column ${table}.${k}`);
  for (const k of def.pk) if (row[k] === undefined || row[k] === null) throw new BadRequest(`missing key ${table}.${k}`);
  const vals = keys.map((k) => toDb(def.cols[k], row[k]));
  const nonPk = keys.filter((k) => !def.pk.includes(k));
  const sets = nonPk.map((k) => `${q(k)} = excluded.${q(k)}`);
  if (def.synced) {
    sets.push(`"updated_at" = ?`, `"sync_version" = ${q(table)}."sync_version" + 1`);
    vals.push(nowIso());
  }
  // Another user's row with the same key must never be overwritten (the RLS WITH CHECK this replaces).
  const guard = def.owned && userId !== null && !def.pk.includes("user_id") ? ` WHERE ${q(table)}."user_id" = ?` : "";
  if (guard) vals.push(userId);
  const sql = `INSERT INTO ${q(table)} (${keys.map(q).join(",")}) VALUES (${keys.map(() => "?").join(",")}) ` +
    (sets.length ? `ON CONFLICT (${def.pk.map(q).join(",")}) DO UPDATE SET ${sets.join(", ")}${guard}`
                 : `ON CONFLICT (${def.pk.map(q).join(",")}) DO NOTHING`);
  return db.prepare(sql).bind(...vals);
}
