import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY") ?? "";
const GROK_API_KEY = Deno.env.get("GROK_API_KEY") ?? "";

interface AiRequest {
  prompt?: string;
  systemPrompt?: string;
  messages?: { role: string; content: string }[];
  responseSchema?: Record<string, unknown>;
  provider: "GEMINI" | "OPENAI" | "GROK";
  enrichContext?: boolean;
  stream?: boolean;
}

interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
}

interface AiResponse {
  text: string;
  provider: string;
  model: string;
  usage?: TokenUsage;
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

// ── User context enrichment ─────────────────────────────────────────────────

function createUserClient(jwt: string) {
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
}

// ── RAG: Vector Embeddings ──────────────────────────────────────────────────

async function embedText(text: string): Promise<number[]> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=${GEMINI_API_KEY}`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "models/text-embedding-004",
      content: { parts: [{ text }] },
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Embedding API error ${response.status}: ${errorText}`);
  }

  const data = await response.json();
  return data?.embedding?.values ?? [];
}

interface SourceRow {
  id: string;
  content: string;
}

function buildGoalText(g: Record<string, unknown>): string {
  const due = g.due_date ? `, Due: ${g.due_date}` : "";
  return `Goal: ${g.title} [${g.category}] - Status: ${g.status}, Progress: ${g.progress}%${due}`;
}

function buildHabitText(h: Record<string, unknown>): string {
  return `Habit: ${h.title} [${h.category}] - ${h.frequency}, Streak: ${h.current_streak} days`;
}

function buildJournalText(j: Record<string, unknown>): string {
  return `Journal (${j.date}): ${j.title} - Mood: ${j.mood}`;
}

function buildMilestoneText(m: Record<string, unknown>): string {
  const status = m.is_completed ? "Completed" : "Pending";
  return `Milestone for goal: ${m.title} - ${status}`;
}

async function syncEmbeddings(jwt: string): Promise<void> {
  if (!jwt || !SUPABASE_URL || !SUPABASE_ANON_KEY || !GEMINI_API_KEY) return;

  try {
    const supabase = createUserClient(jwt);

    // Fetch current source data
    const [goalsRes, habitsRes, journalRes, milestonesRes] = await Promise.all([
      supabase
        .from("goals")
        .select("id, title, category, status, progress, due_date")
        .eq("is_deleted", false)
        .eq("is_archived", false),
      supabase
        .from("habits")
        .select("id, title, category, frequency, current_streak")
        .eq("is_active", true)
        .eq("is_deleted", false),
      supabase
        .from("journal_entries")
        .select("id, title, mood, date")
        .eq("is_deleted", false),
      supabase
        .from("milestones")
        .select("id, title, is_completed")
        .eq("is_deleted", false),
    ]);

    // Build source rows with their text representations
    const sourceRows: { table: string; id: string; content: string }[] = [];

    for (const g of goalsRes.data ?? []) {
      sourceRows.push({ table: "goals", id: g.id, content: buildGoalText(g) });
    }
    for (const h of habitsRes.data ?? []) {
      sourceRows.push({ table: "habits", id: h.id, content: buildHabitText(h) });
    }
    for (const j of journalRes.data ?? []) {
      sourceRows.push({ table: "journal_entries", id: j.id, content: buildJournalText(j) });
    }
    for (const m of milestonesRes.data ?? []) {
      sourceRows.push({ table: "milestones", id: m.id, content: buildMilestoneText(m) });
    }

    // Fetch existing embeddings
    const { data: existingEmbeddings } = await supabase
      .from("content_embeddings")
      .select("id, source_table, source_id, content");

    const existingMap = new Map<string, { id: string; content: string }>();
    for (const e of existingEmbeddings ?? []) {
      existingMap.set(`${e.source_table}:${e.source_id}`, { id: e.id, content: e.content });
    }

    // Find stale/missing embeddings
    const toUpsert: { table: string; id: string; content: string }[] = [];
    const validKeys = new Set<string>();

    for (const row of sourceRows) {
      const key = `${row.table}:${row.id}`;
      validKeys.add(key);
      const existing = existingMap.get(key);
      if (!existing || existing.content !== row.content) {
        toUpsert.push(row);
      }
    }

    // Delete embeddings for removed source rows
    const toDelete: string[] = [];
    for (const e of existingEmbeddings ?? []) {
      const key = `${e.source_table}:${e.source_id}`;
      if (!validKeys.has(key)) {
        toDelete.push(e.id);
      }
    }

    if (toDelete.length > 0) {
      await supabase
        .from("content_embeddings")
        .delete()
        .in("id", toDelete);
    }

    // Rate limit: max 20 new/changed embeddings per sync call
    const batch = toUpsert.slice(0, 20);

    for (const row of batch) {
      try {
        const embedding = await embedText(row.content);
        if (embedding.length === 0) continue;

        await supabase
          .from("content_embeddings")
          .upsert(
            {
              user_id: (await supabase.auth.getUser(jwt)).data.user!.id,
              source_table: row.table,
              source_id: row.id,
              content: row.content,
              embedding: JSON.stringify(embedding),
              updated_at: new Date().toISOString(),
            },
            { onConflict: "user_id,source_table,source_id" }
          );
      } catch (err) {
        console.warn(`Embedding upsert failed for ${row.table}:${row.id}:`, err);
      }
    }
  } catch (err) {
    console.warn("syncEmbeddings failed (non-fatal):", err);
  }
}

async function fetchRelevantContext(
  jwt: string,
  query: string
): Promise<string | null> {
  if (!jwt || !SUPABASE_URL || !SUPABASE_ANON_KEY || !GEMINI_API_KEY) return null;

  try {
    const supabase = createUserClient(jwt);

    // Always fetch profile + progress (small, always relevant)
    const [profileRes, progressRes] = await Promise.all([
      supabase
        .from("users")
        .select("display_name, profession, age_range, mindset")
        .limit(1)
        .single(),
      supabase
        .from("user_progress")
        .select("*")
        .limit(1)
        .single(),
    ]);

    // Embed the query and search for relevant content
    const queryEmbedding = await embedText(query);
    if (queryEmbedding.length === 0) return null;

    const { data: matches, error: matchError } = await supabase.rpc(
      "match_user_content",
      {
        query_embedding: JSON.stringify(queryEmbedding),
        match_count: 10,
        similarity_threshold: 0.3,
      }
    );

    if (matchError) {
      console.warn("match_user_content RPC error:", matchError.message);
      return null;
    }

    // Build context string
    const lines: string[] = ["=== USER DATA ==="];

    // Profile
    const profile = profileRes.data;
    if (profile) {
      const parts = [profile.display_name, profile.profession, profile.age_range].filter(Boolean);
      if (parts.length > 0) lines.push(`PROFILE: ${parts.join(", ")}`);
      if (profile.mindset) lines.push(`MINDSET: ${profile.mindset}`);
    }

    // Progress
    const progress = progressRes.data;
    if (progress) {
      lines.push(
        `PROGRESS: Level ${progress.current_level ?? 1} (${progress.total_xp ?? 0} XP), ` +
          `Streak: ${progress.current_streak ?? 0} days (best: ${progress.longest_streak ?? 0}), ` +
          `Goals completed: ${progress.goals_completed ?? 0}, ` +
          `Habits done: ${progress.habits_completed ?? 0}, ` +
          `Journal entries: ${progress.journal_entries_count ?? 0}`
      );
    }

    // Relevant items from vector search
    if (matches && matches.length > 0) {
      lines.push("");
      lines.push(`RELEVANT CONTEXT (${matches.length} items, ranked by relevance):`);
      for (let i = 0; i < matches.length; i++) {
        const m = matches[i];
        lines.push(`${i + 1}. [${m.source_table}] ${m.content} (relevance: ${(m.similarity * 100).toFixed(0)}%)`);
      }
    }

    lines.push("=== END USER DATA ===");
    return lines.join("\n");
  } catch (err) {
    console.warn("fetchRelevantContext failed:", err);
    return null;
  }
}

function logUsage(jwt: string, result: AiResponse, requestType: string): void {
  if (!jwt || !SUPABASE_URL || !SUPABASE_ANON_KEY) return;
  const client = createUserClient(jwt);
  client
    .from("ai_usage_logs")
    .insert({
      provider: result.provider,
      model: result.model,
      input_tokens: result.usage?.inputTokens ?? 0,
      output_tokens: result.usage?.outputTokens ?? 0,
      request_type: requestType,
    })
    .then(({ error }) => {
      if (error) console.warn("Usage log failed:", error.message);
    });
}

async function fetchUserContext(jwt: string): Promise<string | null> {
  if (!jwt || !SUPABASE_URL || !SUPABASE_ANON_KEY) return null;

  try {
    const supabase = createUserClient(jwt);

    const [
      profileRes,
      progressRes,
      goalsRes,
      habitsRes,
      journalRes,
      badgesRes,
      focusRes,
    ] = await Promise.all([
      supabase
        .from("users")
        .select("display_name, profession, age_range, mindset")
        .limit(1)
        .single(),
      supabase
        .from("user_progress")
        .select("*")
        .limit(1)
        .single(),
      supabase
        .from("goals")
        .select("title, category, status, progress, due_date")
        .eq("is_deleted", false)
        .eq("is_archived", false)
        .limit(20),
      supabase
        .from("habits")
        .select("title, category, current_streak, longest_streak, frequency")
        .eq("is_active", true)
        .eq("is_deleted", false)
        .limit(20),
      supabase
        .from("journal_entries")
        .select("title, mood, date")
        .eq("is_deleted", false)
        .order("date", { ascending: false })
        .limit(10),
      supabase
        .from("badges")
        .select("badge_type, earned_at")
        .eq("is_deleted", false)
        .order("earned_at", { ascending: false })
        .limit(20),
      supabase
        .from("focus_sessions")
        .select(
          "goal_id, planned_duration_minutes, actual_duration_seconds, was_completed, started_at"
        )
        .eq("is_deleted", false)
        .order("started_at", { ascending: false })
        .limit(10),
    ]);

    // If we can't even fetch the profile, skip enrichment
    if (profileRes.error && progressRes.error) {
      console.warn("Context enrichment skipped: couldn't fetch user data");
      return null;
    }

    return formatUserContext(
      profileRes.data,
      progressRes.data,
      goalsRes.data ?? [],
      habitsRes.data ?? [],
      journalRes.data ?? [],
      badgesRes.data ?? [],
      focusRes.data ?? []
    );
  } catch (err) {
    console.warn("Context enrichment failed:", err);
    return null;
  }
}

function formatUserContext(
  profile: Record<string, unknown> | null,
  progress: Record<string, unknown> | null,
  goals: Record<string, unknown>[],
  habits: Record<string, unknown>[],
  journal: Record<string, unknown>[],
  badges: Record<string, unknown>[],
  focus: Record<string, unknown>[]
): string {
  const lines: string[] = ["=== USER DATA ==="];

  // Profile
  if (profile) {
    const parts = [
      profile.display_name,
      profile.profession,
      profile.age_range,
    ].filter(Boolean);
    if (parts.length > 0) lines.push(`PROFILE: ${parts.join(", ")}`);
    if (profile.mindset) lines.push(`MINDSET: ${profile.mindset}`);
  }

  // Progress
  if (progress) {
    lines.push(
      `PROGRESS: Level ${progress.current_level ?? 1} (${progress.total_xp ?? 0} XP), ` +
        `Streak: ${progress.current_streak ?? 0} days (best: ${progress.longest_streak ?? 0}), ` +
        `Goals completed: ${progress.goals_completed ?? 0}, ` +
        `Habits done: ${progress.habits_completed ?? 0}, ` +
        `Journal entries: ${progress.journal_entries_count ?? 0}`
    );
  }

  // Goals — split by status
  const activeGoals = goals.filter(
    (g) => g.status !== "COMPLETED" && g.status !== "ABANDONED"
  );
  const completedGoals = goals.filter((g) => g.status === "COMPLETED");

  if (activeGoals.length > 0) {
    lines.push("");
    lines.push(`ACTIVE GOALS (${activeGoals.length}):`);
    for (let i = 0; i < activeGoals.length; i++) {
      const g = activeGoals[i];
      const due = g.due_date ? ` due ${g.due_date}` : "";
      lines.push(
        `${i + 1}. "${g.title}" [${g.category}] ${g.progress}%${due}`
      );
    }
  }

  if (completedGoals.length > 0) {
    lines.push("");
    lines.push(`COMPLETED GOALS (${completedGoals.length}):`);
    for (let i = 0; i < completedGoals.length; i++) {
      const g = completedGoals[i];
      lines.push(`${i + 1}. "${g.title}" [${g.category}]`);
    }
  }

  // Habits
  if (habits.length > 0) {
    lines.push("");
    lines.push(`HABITS (${habits.length} active):`);
    for (let i = 0; i < habits.length; i++) {
      const h = habits[i];
      lines.push(
        `${i + 1}. "${h.title}" [${h.category}] ${h.frequency} streak:${h.current_streak} best:${h.longest_streak}`
      );
    }
  }

  // Journal
  if (journal.length > 0) {
    lines.push("");
    lines.push(`RECENT JOURNAL (${journal.length}):`);
    for (let i = 0; i < journal.length; i++) {
      const j = journal[i];
      lines.push(`${i + 1}. "${j.title}" ${j.mood} ${j.date}`);
    }
  }

  // Badges
  if (badges.length > 0) {
    lines.push("");
    const badgeTypes = badges.map((b) => b.badge_type).join(", ");
    lines.push(`BADGES (${badges.length}): ${badgeTypes}`);
  }

  // Focus sessions
  if (focus.length > 0) {
    lines.push("");
    lines.push(`RECENT FOCUS SESSIONS (${focus.length}):`);
    for (let i = 0; i < focus.length; i++) {
      const f = focus[i];
      const mins = Math.round(
        (Number(f.actual_duration_seconds) || 0) / 60
      );
      const planned = f.planned_duration_minutes;
      const completed = f.was_completed ? "completed" : "incomplete";
      lines.push(
        `${i + 1}. ${mins}/${planned}min ${completed} (${f.started_at})`
      );
    }
  }

  lines.push("=== END USER DATA ===");
  return lines.join("\n");
}

// ── Main handler ────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers":
          "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  // Validate auth: require a valid Supabase user session
  const authHeader = req.headers.get("authorization") ?? "";
  const jwt = authHeader.replace("Bearer ", "");

  if (!jwt) {
    console.warn("AUTH: No JWT token in request");
    return new Response(
      JSON.stringify({ error: "Authentication required" }),
      { status: 401, headers: { "Content-Type": "application/json" } }
    );
  }

  if (jwt === SUPABASE_ANON_KEY) {
    console.warn("AUTH: Client sent anon key instead of user JWT — session likely expired");
    return new Response(
      JSON.stringify({ error: "Session expired. Please sign in again." }),
      { status: 401, headers: { "Content-Type": "application/json" } }
    );
  }

  // Verify the JWT by calling Supabase auth — rejects expired/invalid tokens
  try {
    const supabase = createUserClient(jwt);
    const { data: { user }, error } = await supabase.auth.getUser(jwt);
    if (error || !user) {
      console.warn("AUTH: getUser failed —", error?.message ?? "no user returned");
      return new Response(
        JSON.stringify({ error: "Invalid or expired token" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    }
  } catch (authErr) {
    console.warn("AUTH: getUser threw —", authErr instanceof Error ? authErr.message : authErr);
    return new Response(
      JSON.stringify({ error: "Auth verification failed" }),
      { status: 401, headers: { "Content-Type": "application/json" } }
    );
  }

  try {
    const body: AiRequest = await req.json();
    const provider = body.provider ?? "GEMINI";

    // Validate provider is in the allowlist
    const ALLOWED_PROVIDERS = ["GEMINI", "OPENAI", "GROK"];
    if (!ALLOWED_PROVIDERS.includes(provider)) {
      return new Response(
        JSON.stringify({ error: `Unsupported provider: ${provider}` }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    if (body.enrichContext !== false && body.messages) {
      try {
        // Sync embeddings first (lazy upsert of stale/missing)
        await syncEmbeddings(jwt);

        // Get the last user message for semantic search
        const lastUserMessage = [...body.messages]
          .reverse()
          .find((m) => m.role === "user")?.content ?? "";

        // Try RAG retrieval first
        let userContext: string | null = null;
        if (lastUserMessage) {
          userContext = await fetchRelevantContext(jwt, lastUserMessage);
        }

        // Fallback to full context dump if RAG returns nothing
        if (!userContext) {
          userContext = await fetchUserContext(jwt);
        }

        if (userContext) {
          const instruction =
            "\n\nYou have access to the user's real-time data below. Use it to answer questions about their goals, habits, streaks, progress, etc. accurately.\n\n" +
            userContext;
          body.systemPrompt = (body.systemPrompt ?? "") + instruction;
        }
      } catch (err) {
        console.warn("Context enrichment error (non-fatal):", err);
      }
    }

    // ── Streaming path ──────────────────────────────────────────────────
    if (body.stream === true && body.messages) {
      return createStreamingResponse(body, provider, jwt);
    }

    // ── Non-streaming path (unchanged) ──────────────────────────────────
    let result: AiResponse;

    switch (provider) {
      case "OPENAI":
        result = await callOpenAI(body);
        break;
      case "GROK":
        result = await callGrok(body);
        break;
      case "GEMINI":
      default:
        result = await callGemini(body);
        break;
    }

    // Fire-and-forget usage logging
    const requestType = body.messages
      ? "chat"
      : body.responseSchema
        ? "structured"
        : "generate";
    logUsage(jwt, result, requestType);

    return new Response(JSON.stringify(result), {
      headers: {
        "Content-Type": "application/json",
        "Connection": "keep-alive",
      },
    });
  } catch (error: unknown) {
    const rawMessage =
      error instanceof Error ? error.message : "Unknown error occurred";
    console.error("AI proxy error:", rawMessage);

    // Sanitize: strip upstream API details (keys, quota info, raw responses)
    let userMessage = "Something went wrong with the AI request. Please try again.";
    if (rawMessage.includes("rate limit") || rawMessage.includes("429")) {
      userMessage = "AI provider is rate-limited. Please wait a moment and try again.";
    } else if (rawMessage.includes("401") || rawMessage.includes("403")) {
      userMessage = "AI provider authentication failed. Please contact support.";
    } else if (rawMessage.includes("timeout") || rawMessage.includes("ETIMEDOUT")) {
      userMessage = "AI provider timed out. Please try again.";
    }

    return new Response(
      JSON.stringify({ error: userMessage }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
});

// ── Gemini ──────────────────────────────────────────────────────────────────

async function callGemini(body: AiRequest): Promise<AiResponse> {
  const model = "gemini-2.0-flash";
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_API_KEY}`;

  const contents = buildGeminiContents(body);

  const generationConfig: Record<string, unknown> = {};
  if (body.responseSchema) {
    generationConfig.responseMimeType = "application/json";
    generationConfig.responseSchema = body.responseSchema;
  }

  const requestBody: Record<string, unknown> = { contents };

  if (body.systemPrompt) {
    requestBody.systemInstruction = {
      parts: [{ text: body.systemPrompt }],
    };
  }

  if (Object.keys(generationConfig).length > 0) {
    requestBody.generationConfig = generationConfig;
  }

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Gemini API error ${response.status}: ${errorText}`);
  }

  const data = await response.json();
  const text =
    data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";

  const usage: TokenUsage | undefined = data?.usageMetadata
    ? {
        inputTokens: data.usageMetadata.promptTokenCount ?? 0,
        outputTokens: data.usageMetadata.candidatesTokenCount ?? 0,
      }
    : undefined;

  return { text, provider: "GEMINI", model, usage };
}

function buildGeminiContents(
  body: AiRequest
): { role: string; parts: { text: string }[] }[] {
  if (body.messages && body.messages.length > 0) {
    return body.messages.map((msg) => ({
      role: msg.role === "assistant" ? "model" : msg.role,
      parts: [{ text: msg.content }],
    }));
  }
  return [{ role: "user", parts: [{ text: body.prompt ?? "" }] }];
}

// ── OpenAI ──────────────────────────────────────────────────────────────────

async function callOpenAI(body: AiRequest): Promise<AiResponse> {
  const model = "gpt-4o-mini";
  const url = "https://api.openai.com/v1/chat/completions";

  // For structured output, append schema to prompt instead of using
  // response_format (which requires additionalProperties:false on every object)
  const messages = buildOpenAIMessages(body);

  const requestBody: Record<string, unknown> = {
    model,
    messages,
  };

  if (body.responseSchema) {
    requestBody.response_format = { type: "json_object" };
  }

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${OPENAI_API_KEY}`,
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`OpenAI API error ${response.status}: ${errorText}`);
  }

  const data = await response.json();
  const text = data?.choices?.[0]?.message?.content ?? "";

  const usage: TokenUsage | undefined = data?.usage
    ? {
        inputTokens: data.usage.prompt_tokens ?? 0,
        outputTokens: data.usage.completion_tokens ?? 0,
      }
    : undefined;

  return { text, provider: "OPENAI", model, usage };
}

function buildOpenAIMessages(
  body: AiRequest
): { role: string; content: string }[] {
  const messages: { role: string; content: string }[] = [];

  if (body.systemPrompt) {
    messages.push({ role: "system", content: body.systemPrompt });
  }

  if (body.messages && body.messages.length > 0) {
    // When using messages with responseSchema, add JSON instruction so OpenAI accepts json_object format
    if (body.responseSchema) {
      messages.push({
        role: "system",
        content: `You must respond in JSON matching this schema:\n${JSON.stringify(body.responseSchema)}`,
      });
    }
    for (const msg of body.messages) {
      messages.push({
        role: msg.role === "model" ? "assistant" : msg.role,
        content: msg.content,
      });
    }
  } else if (body.prompt) {
    let prompt = body.prompt;
    if (body.responseSchema) {
      prompt += `\n\nRespond in this exact JSON schema:\n${JSON.stringify(body.responseSchema)}`;
    }
    messages.push({ role: "user", content: prompt });
  }

  return messages;
}

// ── Streaming ────────────────────────────────────────────────────────────────

type SendFn = (event: string, data: string) => void;

function getModelForProvider(provider: string): string {
  switch (provider) {
    case "OPENAI": return "gpt-4o-mini";
    case "GROK": return "grok-4-1-fast-non-reasoning";
    case "GEMINI":
    default: return "gemini-2.0-flash";
  }
}

function createStreamingResponse(
  body: AiRequest,
  provider: string,
  jwt: string
): Response {
  const model = getModelForProvider(provider);

  const stream = new ReadableStream({
    async start(controller) {
      const encoder = new TextEncoder();
      const send: SendFn = (event: string, data: string) => {
        controller.enqueue(encoder.encode(`event: ${event}\ndata: ${data}\n\n`));
      };

      try {
        let usage: TokenUsage | undefined;

        if (provider === "GEMINI") {
          usage = await streamGemini(body, model, send);
        } else {
          usage = await streamOpenAICompatible(body, provider, model, send);
        }

        const donePayload = JSON.stringify({
          provider,
          model,
          usage: usage ?? { inputTokens: 0, outputTokens: 0 },
        });
        send("done", donePayload);

        // Fire-and-forget usage log
        logUsage(jwt, { text: "", provider, model, usage }, "chat");
      } catch (err: unknown) {
        const rawMsg = err instanceof Error ? err.message : "Stream error";
        console.error("Stream error:", rawMsg);
        let userMsg = "Something went wrong during streaming. Please try again.";
        if (rawMsg.includes("rate limit") || rawMsg.includes("429")) {
          userMsg = "AI provider is rate-limited. Please wait a moment and try again.";
        } else if (rawMsg.includes("timeout") || rawMsg.includes("ETIMEDOUT")) {
          userMsg = "AI provider timed out. Please try again.";
        }
        send("error", JSON.stringify({ error: userMsg }));
      } finally {
        controller.close();
      }
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

async function streamGemini(
  body: AiRequest,
  model: string,
  send: SendFn
): Promise<TokenUsage | undefined> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent?alt=sse&key=${GEMINI_API_KEY}`;

  const contents = buildGeminiContents(body);
  const requestBody: Record<string, unknown> = { contents };

  if (body.systemPrompt) {
    requestBody.systemInstruction = {
      parts: [{ text: body.systemPrompt }],
    };
  }

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Gemini streaming error ${response.status}: ${errorText}`);
  }

  let usage: TokenUsage | undefined;

  // Gemini SSE returns lines like: data: {...}
  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      if (!line.startsWith("data: ")) continue;
      const jsonStr = line.slice(6).trim();
      if (!jsonStr) continue;

      try {
        const chunk = JSON.parse(jsonStr);
        const text = chunk?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (text) {
          send("text", text);
        }

        if (chunk?.usageMetadata) {
          usage = {
            inputTokens: chunk.usageMetadata.promptTokenCount ?? 0,
            outputTokens: chunk.usageMetadata.candidatesTokenCount ?? 0,
          };
        }
      } catch {
        // skip malformed JSON chunks
      }
    }
  }

  return usage;
}

async function streamOpenAICompatible(
  body: AiRequest,
  provider: string,
  model: string,
  send: SendFn
): Promise<TokenUsage | undefined> {
  const isGrok = provider === "GROK";
  const url = isGrok
    ? "https://api.x.ai/v1/chat/completions"
    : "https://api.openai.com/v1/chat/completions";
  const apiKey = isGrok ? GROK_API_KEY : OPENAI_API_KEY;

  const messages = buildOpenAIMessages(body);

  const requestBody: Record<string, unknown> = {
    model,
    messages,
    stream: true,
    stream_options: { include_usage: true },
  };

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`${provider} streaming error ${response.status}: ${errorText}`);
  }

  let usage: TokenUsage | undefined;

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith("data: ")) continue;
      const data = trimmed.slice(6);
      if (data === "[DONE]") continue;

      try {
        const chunk = JSON.parse(data);
        const delta = chunk?.choices?.[0]?.delta?.content;
        if (delta) {
          send("text", delta);
        }

        if (chunk?.usage) {
          usage = {
            inputTokens: chunk.usage.prompt_tokens ?? 0,
            outputTokens: chunk.usage.completion_tokens ?? 0,
          };
        }
      } catch {
        // skip malformed chunks
      }
    }
  }

  return usage;
}

// ── Grok (xAI) ──────────────────────────────────────────────────────────────

async function callGrok(body: AiRequest): Promise<AiResponse> {
  const model = "grok-4-1-fast-non-reasoning";
  const url = "https://api.x.ai/v1/chat/completions";

  const messages = buildOpenAIMessages(body); // Grok uses OpenAI-compatible format

  const requestBody: Record<string, unknown> = {
    model,
    messages,
  };

  if (body.responseSchema) {
    requestBody.response_format = { type: "json_object" };
  }

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${GROK_API_KEY}`,
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Grok API error ${response.status}: ${errorText}`);
  }

  const data = await response.json();
  const text = data?.choices?.[0]?.message?.content ?? "";

  const usage: TokenUsage | undefined = data?.usage
    ? {
        inputTokens: data.usage.prompt_tokens ?? 0,
        outputTokens: data.usage.completion_tokens ?? 0,
      }
    : undefined;

  return { text, provider: "GROK", model, usage };
}
