import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  workers: 1,
  outputDir: "../target/frontend-browser-results",
  reporter: [
    ["list"],
    [
      "html",
      { outputFolder: "../target/frontend-browser-report", open: "never" },
    ],
  ],
  use: {
    baseURL: "http://localhost:4173",
    locale: "ru-RU",
    timezoneId: "Europe/Minsk",
    channel: process.platform === "win32" ? "msedge" : undefined,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { viewport: { width: 1440, height: 1050 } } },
    {
      name: "android",
      use: { ...devices["Pixel 7"], defaultBrowserType: "chromium" },
    },
  ],
  webServer: {
    command: "npm run start",
    url: "http://localhost:4173",
    reuseExistingServer: true,
  },
});
