import { defineConfig, devices } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { config as loadEnv } from 'dotenv';
import { parse } from 'yaml';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
loadEnv({ path: path.resolve(__dirname, '..', '..', '.env'), override: false });
loadEnv({ path: path.resolve(__dirname, '..', '..', '.env.local'), override: true });
loadEnv({ path: path.resolve(__dirname, '.env'), override: false });
loadEnv({ path: path.resolve(__dirname, '.env.local'), override: false });
loadEnv({ path: path.resolve(__dirname, '..', '..', 'manifest.env'), override: false });

/**
 * Default the E2E URLs from Configuration/application.yaml, the shared port settings.
 * Any E2E_* variable set in the environment or a .env file still wins.
 */
type Endpoint = { url?: string; port?: number } | undefined;
const platformConfigPath = path.resolve(__dirname, '..', '..', 'Configuration', 'application.yaml');
const platform = fs.existsSync(platformConfigPath) ? parse(fs.readFileSync(platformConfigPath, 'utf8')) ?? {} : {};
const origin = (entry: Endpoint, fallback: string) =>
  entry?.url ? (entry.port ? `${entry.url}:${entry.port}` : entry.url) : fallback;
process.env.E2E_BASE_URL ||= origin(platform.frontend, 'http://localhost:4200');
process.env.E2E_AUTH_API ||= origin(platform.services?.auth, 'http://localhost:3000');
process.env.E2E_TRADE_API ||= origin(platform.services?.order, 'http://localhost:8081');

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: process.env.E2E_BASE_URL,
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Environment variables for E2E tests */
  webServer: {
    command: 'npm start',
    url: process.env.E2E_BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    // },
  ],


});
