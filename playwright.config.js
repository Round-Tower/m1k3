// Playwright configuration for M1K3 Dashboard Testing
module.exports = {
  testDir: './',
  testMatch: ['test-dashboard-monochrome.js', 'test-dashboard-focused.js'],
  timeout: 30000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['line'],
    ['html', { outputFolder: 'playwright-report' }]
  ],
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    baseURL: 'http://localhost:8082',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...require('@playwright/test').devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...require('@playwright/test').devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...require('@playwright/test').devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...require('@playwright/test').devices['Pixel 5'] },
    },
    {
      name: 'mobile-safari',
      use: { ...require('@playwright/test').devices['iPhone 12'] },
    },
  ],
  webServer: {
    command: 'python3 -m http.server 8082',
    port: 8082,
    reuseExistingServer: !process.env.CI,
  },
};