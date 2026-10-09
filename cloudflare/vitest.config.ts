import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  const migrations = await readD1Migrations("./migrations");
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: { bindings: { TEST_MIGRATIONS: migrations, DEV_AUTH: "1", AUTH_SECRET: "test-secret-test-secret-test-secret-0123", BASE_URL: "http://localhost" } },
      }),
    ],
    test: { setupFiles: ["./test/setup.ts"], include: ["test/**/*.test.ts"] },
  };
});
