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

  // Validate apikey header against the known anon key
  const apikey = req.headers.get("apikey") ?? "";
  if (!apikey || (SUPABASE_ANON_KEY && apikey !== SUPABASE_ANON_KEY)) {
    return new Response(
      JSON.stringify({ error: "Unauthorized: invalid or missing apikey" }),
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

    // Enrich system prompt with user context if requested
    const authHeader = req.headers.get("authorization") ?? "";
    const jwt = authHeader.replace("Bearer ", "");

    if (body.enrichContext !== false && body.messages) {
      try {
        const userContext = await fetchUserContext(jwt);
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
    const message =
      error instanceof Error ? error.message : "Unknown error occurred";
    console.error("AI proxy error:", message);
    return new Response(
      JSON.stringify({ error: message }),
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
