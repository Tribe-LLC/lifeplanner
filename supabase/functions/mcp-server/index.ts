import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

// ── Types ────────────────────────────────────────────────────────────────────

interface JSONRPCRequest {
  jsonrpc: "2.0";
  id: string | number;
  method: string;
  params?: Record<string, unknown>;
}

interface JSONRPCResponse {
  jsonrpc: "2.0";
  id: string | number;
  result?: unknown;
  error?: {
    code: number;
    message: string;
    data?: unknown;
  };
}

interface MCPTool {
  name: string;
  description: string;
  inputSchema: {
    type: "object";
    properties: Record<string, unknown>;
    required: string[];
  };
}

interface TextContent {
  type: "text";
  text: string;
}

interface MCPResponse {
  content: TextContent[];
}

// ── Supabase Client Creation ────────────────────────────────────────────────

function createUserClient(jwt: string) {
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
}

// ── MCP Tool Definitions ────────────────────────────────────────────────────

const MCP_TOOLS: MCPTool[] = [
  {
    name: "get_dashboard",
    description:
      "Returns an overview: user profile, progress stats, active goals count, habit streaks, recent journal moods",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "list_goals",
    description:
      "List goals with optional filters: category, status, timeline. Excludes deleted/archived by default.",
    inputSchema: {
      type: "object",
      properties: {
        category: {
          type: "string",
          description: "Optional filter by goal category",
        },
        status: {
          type: "string",
          description: "Optional filter by status (e.g., ACTIVE, COMPLETED)",
        },
        timeline: {
          type: "string",
          description: "Optional filter by timeline",
        },
      },
      required: [],
    },
  },
  {
    name: "get_goal",
    description: "Get a single goal by ID with its milestones",
    inputSchema: {
      type: "object",
      properties: {
        goal_id: {
          type: "string",
          description: "The goal ID",
        },
      },
      required: ["goal_id"],
    },
  },
  {
    name: "list_habits",
    description:
      "List habits with optional category filter. Shows streaks.",
    inputSchema: {
      type: "object",
      properties: {
        category: {
          type: "string",
          description: "Optional filter by habit category",
        },
      },
      required: [],
    },
  },
  {
    name: "get_habit_history",
    description:
      "Get check-in history for a habit, with optional date range",
    inputSchema: {
      type: "object",
      properties: {
        habit_id: {
          type: "string",
          description: "The habit ID",
        },
        from_date: {
          type: "string",
          description: "Optional start date (ISO format)",
        },
        to_date: {
          type: "string",
          description: "Optional end date (ISO format)",
        },
      },
      required: ["habit_id"],
    },
  },
  {
    name: "list_journal_entries",
    description:
      "List journal entries with optional mood filter and date range, ordered by date desc",
    inputSchema: {
      type: "object",
      properties: {
        mood: {
          type: "string",
          description: "Optional filter by mood",
        },
        from_date: {
          type: "string",
          description: "Optional start date (ISO format)",
        },
        to_date: {
          type: "string",
          description: "Optional end date (ISO format)",
        },
      },
      required: [],
    },
  },
  {
    name: "get_journal_entry",
    description: "Get a single journal entry by ID",
    inputSchema: {
      type: "object",
      properties: {
        entry_id: {
          type: "string",
          description: "The journal entry ID",
        },
      },
      required: ["entry_id"],
    },
  },
  {
    name: "get_progress",
    description: "Get user gamification stats (XP, level, streak, completion counts)",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "list_badges",
    description: "List earned badges",
    inputSchema: {
      type: "object",
      properties: {},
      required: [],
    },
  },
  {
    name: "list_challenges",
    description: "List challenges with optional is_completed filter",
    inputSchema: {
      type: "object",
      properties: {
        is_completed: {
          type: "boolean",
          description: "Optional filter by completion status",
        },
      },
      required: [],
    },
  },
  {
    name: "list_focus_sessions",
    description: "List focus sessions with optional date range",
    inputSchema: {
      type: "object",
      properties: {
        from_date: {
          type: "string",
          description: "Optional start date (ISO format)",
        },
        to_date: {
          type: "string",
          description: "Optional end date (ISO format)",
        },
      },
      required: [],
    },
  },
  {
    name: "list_review_reports",
    description: "List AI-generated review reports with optional type filter",
    inputSchema: {
      type: "object",
      properties: {
        type: {
          type: "string",
          description: "Optional filter by report type",
        },
      },
      required: [],
    },
  },
  {
    name: "search_goals",
    description: "Search goals by title/description text",
    inputSchema: {
      type: "object",
      properties: {
        query: {
          type: "string",
          description: "Search query text",
        },
      },
      required: ["query"],
    },
  },
];

// ── Tool Implementations ────────────────────────────────────────────────────

async function handleGetDashboard(
  supabase: ReturnType<typeof createUserClient>
): Promise<string> {
  const [profile, progress, goalsRes, habitsRes, journalRes] = await Promise.all([
    supabase
      .from("users")
      .select("display_name, selected_symbol, profession, age_range")
      .limit(1)
      .single(),
    supabase
      .from("user_progress")
      .select("*")
      .limit(1)
      .single(),
    supabase
      .from("goals")
      .select("id, title, category, status, progress")
      .eq("is_deleted", false)
      .eq("is_archived", false),
    supabase
      .from("habits")
      .select("id, title, current_streak, longest_streak")
      .eq("is_active", true)
      .eq("is_deleted", false),
    supabase
      .from("journal_entries")
      .select("mood, date")
      .eq("is_deleted", false)
      .order("date", { ascending: false })
      .limit(10),
  ]);

  const result: Record<string, unknown> = {};

  if (profile.data) {
    result.profile = profile.data;
  }
  if (progress.data) {
    result.progress = progress.data;
  }

  const activeGoals = (goalsRes.data ?? []).filter((g) => g.status !== "COMPLETED");
  result.active_goals_count = activeGoals.length;
  result.goals_total = goalsRes.data?.length ?? 0;

  if ((habitsRes.data ?? []).length > 0) {
    result.habit_streaks = (habitsRes.data ?? []).map((h) => ({
      title: h.title,
      current_streak: h.current_streak,
      longest_streak: h.longest_streak,
    }));
  }

  if ((journalRes.data ?? []).length > 0) {
    result.recent_moods = (journalRes.data ?? []).map((j) => ({
      mood: j.mood,
      date: j.date,
    }));
  }

  return JSON.stringify(result, null, 2);
}

async function handleListGoals(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("goals")
    .select(
      "id, title, description, category, status, timeline, due_date, progress, completion_rate, is_archived"
    )
    .eq("is_deleted", false)
    .eq("is_archived", false);

  if (args.category && typeof args.category === "string") {
    query = query.eq("category", args.category);
  }
  if (args.status && typeof args.status === "string") {
    query = query.eq("status", args.status);
  }
  if (args.timeline && typeof args.timeline === "string") {
    query = query.eq("timeline", args.timeline);
  }

  const { data, error } = await query.order("created_at", { ascending: false });

  if (error) throw new Error(`Failed to list goals: ${error.message}`);
  return JSON.stringify(data ?? [], null, 2);
}

async function handleGetGoal(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  const goalId = args.goal_id as string;

  const [goalRes, milestonesRes] = await Promise.all([
    supabase
      .from("goals")
      .select("*")
      .eq("id", goalId)
      .eq("is_deleted", false)
      .single(),
    supabase
      .from("milestones")
      .select("*")
      .eq("goal_id", goalId)
      .eq("is_deleted", false)
      .order("created_at", { ascending: false }),
  ]);

  if (goalRes.error) {
    throw new Error(`Goal not found: ${goalRes.error.message}`);
  }

  const result = {
    goal: goalRes.data,
    milestones: milestonesRes.data ?? [],
  };

  return JSON.stringify(result, null, 2);
}

async function handleListHabits(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("habits")
    .select("id, title, description, category, frequency, current_streak, longest_streak, total_completions, is_active")
    .eq("is_active", true)
    .eq("is_deleted", false);

  if (args.category && typeof args.category === "string") {
    query = query.eq("category", args.category);
  }

  const { data, error } = await query.order("current_streak", { ascending: false });

  if (error) throw new Error(`Failed to list habits: ${error.message}`);
  return JSON.stringify(data ?? [], null, 2);
}

async function handleGetHabitHistory(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  const habitId = args.habit_id as string;

  let query = supabase
    .from("habit_check_ins")
    .select("id, date, completed, notes")
    .eq("habit_id", habitId)
    .eq("is_deleted", false);

  if (args.from_date && typeof args.from_date === "string") {
    query = query.gte("date", args.from_date);
  }
  if (args.to_date && typeof args.to_date === "string") {
    query = query.lte("date", args.to_date);
  }

  const { data, error } = await query.order("date", { ascending: false });

  if (error) {
    throw new Error(`Failed to get habit history: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleListJournalEntries(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("journal_entries")
    .select("id, title, content, mood, date, tags, linked_goal_id, linked_habit_id")
    .eq("is_deleted", false);

  if (args.mood && typeof args.mood === "string") {
    query = query.eq("mood", args.mood);
  }
  if (args.from_date && typeof args.from_date === "string") {
    query = query.gte("date", args.from_date);
  }
  if (args.to_date && typeof args.to_date === "string") {
    query = query.lte("date", args.to_date);
  }

  const { data, error } = await query.order("date", { ascending: false });

  if (error) {
    throw new Error(`Failed to list journal entries: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleGetJournalEntry(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  const entryId = args.entry_id as string;

  const { data, error } = await supabase
    .from("journal_entries")
    .select("*")
    .eq("id", entryId)
    .eq("is_deleted", false)
    .single();

  if (error) {
    throw new Error(`Journal entry not found: ${error.message}`);
  }

  return JSON.stringify(data, null, 2);
}

async function handleGetProgress(
  supabase: ReturnType<typeof createUserClient>
): Promise<string> {
  const { data, error } = await supabase
    .from("user_progress")
    .select("*")
    .limit(1)
    .single();

  if (error) {
    throw new Error(`Failed to get progress: ${error.message}`);
  }

  return JSON.stringify(data ?? {}, null, 2);
}

async function handleListBadges(
  supabase: ReturnType<typeof createUserClient>
): Promise<string> {
  const { data, error } = await supabase
    .from("badges")
    .select("id, badge_type, earned_at, is_new")
    .eq("is_deleted", false)
    .order("earned_at", { ascending: false });

  if (error) {
    throw new Error(`Failed to list badges: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleListChallenges(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("challenges")
    .select("id, challenge_type, start_date, end_date, current_progress, target_progress, is_completed, xp_earned")
    .eq("is_deleted", false);

  if (typeof args.is_completed === "boolean") {
    query = query.eq("is_completed", args.is_completed);
  }

  const { data, error } = await query.order("start_date", { ascending: false });

  if (error) {
    throw new Error(`Failed to list challenges: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleListFocusSessions(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("focus_sessions")
    .select(
      "id, goal_id, milestone_id, planned_duration_minutes, actual_duration_seconds, was_completed, xp_earned, started_at, completed_at, mood, ambient_sound, focus_theme"
    )
    .eq("is_deleted", false);

  if (args.from_date && typeof args.from_date === "string") {
    query = query.gte("started_at", args.from_date);
  }
  if (args.to_date && typeof args.to_date === "string") {
    query = query.lte("started_at", args.to_date);
  }

  const { data, error } = await query.order("started_at", { ascending: false });

  if (error) {
    throw new Error(`Failed to list focus sessions: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleListReviewReports(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  let query = supabase
    .from("review_reports")
    .select(
      "id, type, period_start, period_end, generated_at, summary, highlights_json, insights_json, recommendations_json, stats_json, is_read"
    )
    .eq("is_deleted", false);

  if (args.type && typeof args.type === "string") {
    query = query.eq("type", args.type);
  }

  const { data, error } = await query.order("generated_at", { ascending: false });

  if (error) {
    throw new Error(`Failed to list review reports: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

async function handleSearchGoals(
  supabase: ReturnType<typeof createUserClient>,
  args: Record<string, unknown>
): Promise<string> {
  const query = (args.query as string) || "";

  if (!query.trim()) {
    return JSON.stringify([], null, 2);
  }

  const { data, error } = await supabase
    .from("goals")
    .select("id, title, description, category, status, progress")
    .eq("is_deleted", false)
    .eq("is_archived", false)
    .or(`title.ilike.%${query}%,description.ilike.%${query}%`)
    .order("created_at", { ascending: false });

  if (error) {
    throw new Error(`Failed to search goals: ${error.message}`);
  }

  return JSON.stringify(data ?? [], null, 2);
}

// ── Tool Router ─────────────────────────────────────────────────────────────

async function callTool(
  jwt: string,
  toolName: string,
  args: Record<string, unknown>
): Promise<string> {
  const supabase = createUserClient(jwt);

  switch (toolName) {
    case "get_dashboard":
      return await handleGetDashboard(supabase);
    case "list_goals":
      return await handleListGoals(supabase, args);
    case "get_goal":
      return await handleGetGoal(supabase, args);
    case "list_habits":
      return await handleListHabits(supabase, args);
    case "get_habit_history":
      return await handleGetHabitHistory(supabase, args);
    case "list_journal_entries":
      return await handleListJournalEntries(supabase, args);
    case "get_journal_entry":
      return await handleGetJournalEntry(supabase, args);
    case "get_progress":
      return await handleGetProgress(supabase);
    case "list_badges":
      return await handleListBadges(supabase);
    case "list_challenges":
      return await handleListChallenges(supabase, args);
    case "list_focus_sessions":
      return await handleListFocusSessions(supabase, args);
    case "list_review_reports":
      return await handleListReviewReports(supabase, args);
    case "search_goals":
      return await handleSearchGoals(supabase, args);
    default:
      throw new Error(`Unknown tool: ${toolName}`);
  }
}

// ── JSON-RPC Handler ────────────────────────────────────────────────────────

async function handleJSONRPC(
  jwt: string,
  request: JSONRPCRequest
): Promise<JSONRPCResponse> {
  try {
    if (request.method === "initialize") {
      return {
        jsonrpc: "2.0",
        id: request.id,
        result: {
          protocolVersion: "2024-11-05",
          capabilities: {},
          serverInfo: {
            name: "lifeplanner-mcp-server",
            version: "1.0.0",
          },
        },
      };
    }

    if (request.method === "tools/list") {
      return {
        jsonrpc: "2.0",
        id: request.id,
        result: {
          tools: MCP_TOOLS,
        },
      };
    }

    if (request.method === "tools/call") {
      const toolName = (request.params?.name as string) || "";
      const toolArgs = (request.params?.arguments as Record<string, unknown>) || {};

      if (!toolName) {
        return {
          jsonrpc: "2.0",
          id: request.id,
          error: {
            code: -32602,
            message: "Missing tool name",
          },
        };
      }

      const tool = MCP_TOOLS.find((t) => t.name === toolName);
      if (!tool) {
        return {
          jsonrpc: "2.0",
          id: request.id,
          error: {
            code: -32601,
            message: `Tool not found: ${toolName}`,
          },
        };
      }

      try {
        const toolResult = await callTool(jwt, toolName, toolArgs);
        return {
          jsonrpc: "2.0",
          id: request.id,
          result: {
            content: [
              {
                type: "text",
                text: toolResult,
              },
            ],
          },
        };
      } catch (toolError) {
        const errorMsg =
          toolError instanceof Error ? toolError.message : "Unknown tool error";
        return {
          jsonrpc: "2.0",
          id: request.id,
          error: {
            code: -32603,
            message: errorMsg,
          },
        };
      }
    }

    return {
      jsonrpc: "2.0",
      id: request.id,
      error: {
        code: -32601,
        message: `Unknown method: ${request.method}`,
      },
    };
  } catch (err) {
    const errorMsg = err instanceof Error ? err.message : "Unknown error";
    return {
      jsonrpc: "2.0",
      id: request.id,
      error: {
        code: -32603,
        message: errorMsg,
      },
    };
  }
}

// ── Request Handler ────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  // CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers":
          "authorization, x-client-info, apikey, content-type",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
      },
    });
  }

  // Only POST is supported
  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ error: "Method not allowed" }),
      {
        status: 405,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // Extract and validate JWT
  const authHeader = req.headers.get("authorization") ?? "";
  const jwt = authHeader.replace("Bearer ", "");

  if (!jwt) {
    return new Response(
      JSON.stringify({
        jsonrpc: "2.0",
        error: { code: -32603, message: "Authentication required" },
      }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  if (jwt === SUPABASE_ANON_KEY) {
    return new Response(
      JSON.stringify({
        jsonrpc: "2.0",
        error: {
          code: -32603,
          message: "Session expired. Please sign in again.",
        },
      }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // Verify JWT
  try {
    const supabase = createUserClient(jwt);
    const { data: { user }, error } = await supabase.auth.getUser(jwt);
    if (error || !user) {
      console.warn("AUTH: getUser failed —", error?.message ?? "no user returned");
      return new Response(
        JSON.stringify({
          jsonrpc: "2.0",
          error: {
            code: -32603,
            message: "Invalid or expired token",
          },
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        }
      );
    }
  } catch (authErr) {
    console.warn(
      "AUTH: getUser threw —",
      authErr instanceof Error ? authErr.message : authErr
    );
    return new Response(
      JSON.stringify({
        jsonrpc: "2.0",
        error: {
          code: -32603,
          message: "Auth verification failed",
        },
      }),
      {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  // Parse JSON-RPC request
  try {
    const request: JSONRPCRequest = await req.json();

    // Validate JSON-RPC format
    if (request.jsonrpc !== "2.0") {
      return new Response(
        JSON.stringify({
          jsonrpc: "2.0",
          error: {
            code: -32600,
            message: "Invalid Request: jsonrpc field must be 2.0",
          },
        }),
        {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    if (typeof request.id === "undefined") {
      return new Response(
        JSON.stringify({
          jsonrpc: "2.0",
          error: {
            code: -32600,
            message: "Invalid Request: missing id field",
          },
        }),
        {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    if (!request.method) {
      return new Response(
        JSON.stringify({
          jsonrpc: "2.0",
          id: request.id,
          error: {
            code: -32600,
            message: "Invalid Request: missing method field",
          },
        }),
        {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    // Handle the request
    const response = await handleJSONRPC(jwt, request);

    return new Response(JSON.stringify(response), {
      headers: {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
      },
    });
  } catch (parseErr) {
    console.error("JSON parse error:", parseErr);
    return new Response(
      JSON.stringify({
        jsonrpc: "2.0",
        error: {
          code: -32700,
          message: "Parse error",
        },
      }),
      {
        status: 400,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
});
