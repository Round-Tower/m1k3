/**
 * M1K3 Monochrome Dashboard - Focused Test Suite
 * 
 * Streamlined tests for key functionality validation
 */

const { test, expect } = require('@playwright/test');

// Test configuration
const DASHBOARD_URL = 'http://localhost:8082/realtime_dashboard.html';

test.describe('M1K3 Monochrome Dashboard - Focused Tests', () => {
  
  test('Basic dashboard loading and structure', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Basic page load
    await expect(page).toHaveTitle(/M1K3 Real-Time Dashboard/);
    await expect(page.locator('body')).toBeVisible();
    
    // Core structure elements
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('footer')).toBeVisible();
    
    // M1K3 title
    await expect(page.locator('h1')).toContainText('M1K3');
    
    // Version display (more specific selector)
    await expect(page.locator('header .text-gray-light')).toContainText('v2.0.1');
  });

  test('Monochrome color scheme validation', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test body background is black
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(0, 0, 0)');
    
    // Test main text is white
    await expect(page.locator('h1')).toHaveCSS('color', 'rgb(255, 255, 255)');
    
    // Test panels have dark background
    const panel = page.locator('.panel').first();
    await expect(panel).toHaveCSS('background-color', 'rgb(10, 10, 10)');
  });

  test('Grid layout responsiveness', async ({ page }) => {
    // Desktop test
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(DASHBOARD_URL);
    
    // Should have 3-column layout on desktop
    await expect(page.locator('.col-3')).toHaveCount(2); // Left and right sidebars
    await expect(page.locator('.col-6')).toHaveCount(1); // Main content
    
    // Mobile test
    await page.setViewportSize({ width: 375, height: 667 });
    await page.reload();
    
    // All sections should still be visible on mobile
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('aside')).toHaveCount(2);
  });

  test('Tab navigation functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test tab switching
    const chatTab = page.locator('#chatTab');
    const settingsTab = page.locator('#settingsTab');
    const debugTab = page.locator('#debugTab');
    
    await expect(chatTab).toBeVisible();
    await chatTab.click();
    
    // Chat content should be active
    await expect(page.locator('#chatContent')).toHaveClass(/active/);
    
    // Switch to settings
    await settingsTab.click();
    await expect(page.locator('#settingsContent')).toHaveClass(/active/);
    
    // Switch to debug
    await debugTab.click();
    await expect(page.locator('#debugContent')).toHaveClass(/active/);
  });

  test('Emotion controls functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test emotion buttons
    const happyBtn = page.locator('[data-emotion="happy"]');
    const sadBtn = page.locator('[data-emotion="sad"]');
    
    await expect(happyBtn).toBeVisible();
    await happyBtn.click();
    
    // Should update emotion display
    await expect(page.locator('#currentEmotion')).toContainText('happy');
    
    // Test intensity slider
    const intensitySlider = page.locator('#emotionIntensity');
    await intensitySlider.fill('75');
    await expect(page.locator('#emotionIntensityValue')).toContainText('75');
  });

  test('Chat interface functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Navigate to chat
    await page.locator('#chatTab').click();
    
    // Test chat input
    const chatInput = page.locator('#chatInput');
    const sendButton = page.locator('#sendButton');
    
    await expect(chatInput).toBeVisible();
    await expect(sendButton).toBeVisible();
    
    // Send a message
    await chatInput.fill('Test message');
    await sendButton.click();
    
    // Check message appears
    await expect(page.locator('#chatMessages')).toContainText('Test message');
  });

  test('Avatar canvas elements present', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Avatar canvases should exist
    await expect(page.locator('#avatarCanvas')).toBeVisible();
    await expect(page.locator('#particleCanvas')).toBeVisible();
    
    // Check dimensions
    await expect(page.locator('#avatarCanvas')).toHaveAttribute('width', '150');
    await expect(page.locator('#avatarCanvas')).toHaveAttribute('height', '150');
  });

  test('System metrics display', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // System metrics should be visible
    await expect(page.locator('#cpuUsage')).toBeVisible();
    await expect(page.locator('#memoryUsage')).toBeVisible();
    await expect(page.locator('#temperature')).toBeVisible();
    await expect(page.locator('#battery')).toBeVisible();
    
    // Should have default values
    await expect(page.locator('#cpuUsage')).toContainText('0%');
    await expect(page.locator('#temperature')).toContainText('25°C');
  });

  test('Accessibility: Focus indicators', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test focus on buttons
    const minimizeBtn = page.locator('#minimize-btn');
    await minimizeBtn.focus();
    
    // Should have focus outline
    const outlineColor = await minimizeBtn.evaluate(el => 
      window.getComputedStyle(el).outlineColor
    );
    expect(outlineColor).toBe('rgb(255, 255, 255)');
  });

  test('Footer status elements', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Footer status should be visible
    await expect(page.locator('#footerWsStatus')).toBeVisible();
    await expect(page.locator('#footerUptime')).toBeVisible();
    await expect(page.locator('#footerEcoSavings')).toBeVisible();
    
    // Default values
    await expect(page.locator('#footerWsStatus')).toContainText('Disconnected');
    await expect(page.locator('#footerUptime')).toContainText('0s');
  });

  test('Interactive buttons are functional', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Header buttons
    await expect(page.locator('#minimize-btn')).toBeVisible();
    await expect(page.locator('#fullscreen-btn')).toBeVisible();
    await expect(page.locator('#close-btn')).toBeVisible();
    
    // Avatar control buttons
    await expect(page.locator('#testEmotions')).toBeVisible();
    await expect(page.locator('#randomizeAvatar')).toBeVisible();
    await expect(page.locator('#resetAvatar')).toBeVisible();
    
    // All should be enabled
    await expect(page.locator('#testEmotions')).toBeEnabled();
    await expect(page.locator('#randomizeAvatar')).toBeEnabled();
    await expect(page.locator('#resetAvatar')).toBeEnabled();
  });

  test('Full integration workflow', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // 1. Verify dashboard loads
    await expect(page.locator('h1')).toContainText('M1K3');
    
    // 2. Switch to chat tab
    await page.locator('#chatTab').click();
    await expect(page.locator('#chatContent')).toHaveClass(/active/);
    
    // 3. Send a message
    await page.locator('#chatInput').fill('Integration test message');
    await page.locator('#sendButton').click();
    await expect(page.locator('#chatMessages')).toContainText('Integration test message');
    
    // 4. Test emotion control
    await page.locator('[data-emotion="excited"]').click();
    await expect(page.locator('#currentEmotion')).toContainText('excited');
    
    // 5. Test intensity slider
    await page.locator('#emotionIntensity').fill('90');
    await expect(page.locator('#emotionIntensityValue')).toContainText('90');
    
    // 6. Verify all main sections still visible
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('footer')).toBeVisible();
  });
});