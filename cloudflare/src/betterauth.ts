import { betterAuth } from "better-auth";
import { anonymous, bearer, emailOTP } from "better-auth/plugins";
import { createAuthMiddleware } from "better-auth/api";
import { hashPassword, verifyPassword } from "better-auth/crypto";
import bcrypt from "bcryptjs";
import { sendEmail } from "./email";
import type { Env } from "./auth";

/** Tables that carry a user_id and move with the user (used when a guest signs in over an existing account). */
export const MODEL_NAMES = { user: "auth_user", session: "auth_session", account: "auth_account", verification: "auth_verification" };

export function createAuth(env: Env, baseURL?: string) {
  const googleIds = [env.GOOGLE_WEB_CLIENT_ID, env.GOOGLE_ANDROID_CLIENT_ID, env.GOOGLE_IOS_CLIENT_ID].filter(Boolean) as string[];
  return betterAuth({
    baseURL: baseURL ?? env.BASE_URL,
    basePath: "/v1/auth",
    secret: env.AUTH_SECRET,
    database: env.DB,
    user: { modelName: MODEL_NAMES.user, additionalFields: {} },
    session: { modelName: MODEL_NAMES.session, expiresIn: 60 * 60 * 24 * 90, updateAge: 60 * 60 * 24 },
    account: { modelName: MODEL_NAMES.account, accountLinking: { enabled: true, trustedProviders: ["google", "apple"] } },
    verification: { modelName: MODEL_NAMES.verification },
    emailAndPassword: {
      enabled: true,
      minPasswordLength: 6, // Supabase's minimum; existing users keep working
      password: {
        hash: (password) => hashPassword(password),
        // Supabase imported accounts carry bcrypt ($2a$); everyone else uses Better Auth's scrypt.
        verify: async ({ hash, password }) =>
          hash.startsWith("$2") ? bcrypt.compare(password, hash) : verifyPassword({ hash, password }),
      },
      sendResetPassword: async ({ user, token }) => {
        await sendEmail(env, { to: user.email, subject: "Reset your LifePlanner password", text: `Your reset code: ${token}` });
      },
    },
    hooks: {
      // First successful password login of an imported Supabase account: replace the bcrypt hash with scrypt.
      after: createAuthMiddleware(async (ctx) => {
        const user = ctx.context.newSession?.user;
        if (ctx.path !== "/sign-in/email" || !user) return;
        const password = (ctx.body as { password?: string } | undefined)?.password;
        const row = await env.DB.prepare(`SELECT password FROM auth_account WHERE userId = ? AND providerId = 'credential'`)
          .bind(user.id).first<{ password: string | null }>();
        if (password && row?.password?.startsWith("$2")) await ctx.context.internalAdapter.updatePassword(user.id, await hashPassword(password));
      }),
    },
    socialProviders: {
      ...(googleIds.length ? { google: { clientId: googleIds as any, clientSecret: env.GOOGLE_WEB_CLIENT_SECRET ?? "" } } : {}),
      ...(env.APPLE_SERVICE_ID
        // ID-token sign-in (what the iOS app uses) only verifies against Apple's keys; the client secret JWT is
        // needed later for token revocation on account deletion.
        ? { apple: { clientId: env.APPLE_SERVICE_ID, clientSecret: env.APPLE_CLIENT_SECRET ?? "", appBundleIdentifier: env.APPLE_BUNDLE_ID } }
        : {}),
    },
    plugins: [
      anonymous({
        onLinkAccount: async ({ anonymousUser, newUser }) => {
          // A guest signed in over an existing account: move the guest's rows to it.
          await moveUserData(env.DB, anonymousUser.user.id, newUser.user.id);
        },
      }),
      emailOTP({
        otpLength: 6,
        expiresIn: 600,
        sendVerificationOTP: async ({ email, otp, type }) => {
          const subject = type === "sign-in" ? "Your LifePlanner sign-in code" : type === "email-verification" ? "Verify your email" : "Your LifePlanner code";
          await sendEmail(env, { to: email, subject, text: `Your code is ${otp}. It expires in 10 minutes.` });
        },
      }),
      bearer(),
    ],
  });
}

import { TABLES } from "./tables";
/** Re-key every owned row from one user id to another in one atomic batch. */
export async function moveUserData(db: D1Database, from: string, to: string) {
  const stmts: D1PreparedStatement[] = [];
  for (const [name, def] of Object.entries(TABLES)) {
    if (!def.owned) continue;
    // OR IGNORE: unique keys (badges per type, one user_progress/users row) keep the destination's row.
    stmts.push(db.prepare(`UPDATE OR IGNORE "${name}" SET "user_id" = ? WHERE "user_id" = ?`).bind(to, from));
    stmts.push(db.prepare(`DELETE FROM "${name}" WHERE "user_id" = ?`).bind(from));
  }
  await db.batch(stmts);
}
