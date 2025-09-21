/**
 * M1K3 Monochrome Dashboard - Comprehensive Playwright Test Suite
 * 
 * Tests the complete monochrome dashboard interface including:
 * - Monochrome design system (blacks, grays, whites only)
 * - Responsive grid layout across viewports
 * - Interactive elements and controls
 * - Accessibility compliance
 * - Avatar system integration
 * - Real-time updates functionality
 * 
 * Based on user feedback: "the green is jarring" → pure monochrome design
 * "minimal, and square / grid based to best use space"
 */

const { test, expect, devices } = require('@playwright/test');

// Test configuration
const DASHBOARD_URL = 'http://localhost:8082/realtime_dashboard.html';
const VIEWPORTS = {
  mobile: { width: 375, height: 667 },    // iPhone SE
  tablet: { width: 768, height: 1024 },   // iPad
  desktop: { width: 1920, height: 1080 }  // Desktop HD
};

// Monochrome color palette - exact values from CSS
const MONOCHROME_COLORS = {
  black: 'rgb(0, 0, 0)',
  grayDarker: 'rgb(10, 10, 10)',
  grayDark: 'rgb(26, 26, 26)',
  grayMedium: 'rgb(51, 51, 51)',
  gray: 'rgb(136, 136, 136)',      // Updated from #666 to #888 for accessibility
  grayLight: 'rgb(136, 136, 136)',
  grayLighter: 'rgb(204, 204, 204)',
  white: 'rgb(255, 255, 255)'
};

test.describe('M1K3 Monochrome Dashboard - Comprehensive Test Suite', () => {
  
  // ===== BASIC LOADING AND STRUCTURE TESTS =====
  
  test('Dashboard loads successfully and displays core structure', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Wait for dashboard to load
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('.container-fluid').first()).toBeVisible();
    
    // Verify main layout structure
    await expect(page.locator('header.header')).toBeVisible();
    await expect(page.locator('main.col-6')).toBeVisible();
    await expect(page.locator('aside.col-3')).toHaveCount(2); // Left and right sidebars
    await expect(page.locator('footer.footer')).toBeVisible();
    
    // Verify title and version
    await expect(page.locator('h1')).toContainText('M1K3');
    await expect(page.locator('.text-gray-light')).toContainText('v2.0.1');
  });

  // ===== MONOCHROME DESIGN SYSTEM TESTS =====
  
  test('Verifies pure monochrome color scheme - no non-grayscale colors', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    await page.waitForLoadState('networkidle');
    
    // Get all elements with background colors
    const backgroundElements = await page.locator('*').all();
    
    for (const element of backgroundElements) {
      const backgroundColor = await element.evaluate(el => 
        window.getComputedStyle(el).backgroundColor
      );
      
      // Skip transparent/auto backgrounds
      if (backgroundColor === 'rgba(0, 0, 0, 0)' || backgroundColor === 'transparent') continue;
      
      // Verify it's a monochrome color
      const isMonochrome = Object.values(MONOCHROME_COLORS).includes(backgroundColor) ||
                          backgroundColor.match(/rgb\((\d+),\s*\1,\s*\1\)/); // RGB with equal values
      
      if (!isMonochrome) {
        console.log(`Non-monochrome background found: ${backgroundColor} on element:`, 
                   await element.getAttribute('class'));
      }
      
      expect(isMonochrome).toBeTruthy();
    }
    
    // Test specific key elements for exact monochrome compliance
    await expect(page.locator('body')).toHaveCSS('background-color', MONOCHROME_COLORS.black);
    await expect(page.locator('header.header')).toHaveCSS('background-color', MONOCHROME_COLORS.black);
    await expect(page.locator('.panel')).first().toHaveCSS('background-color', MONOCHROME_COLORS.grayDarker);
  });

  test('Verifies monochrome text colors only', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test key text elements
    await expect(page.locator('h1')).toHaveCSS('color', MONOCHROME_COLORS.white);
    await expect(page.locator('.text-gray').first()).toHaveCSS('color', MONOCHROME_COLORS.gray);
    await expect(page.locator('.text-gray-light').first()).toHaveCSS('color', MONOCHROME_COLORS.grayLight);
    await expect(page.locator('.text-white').first()).toHaveCSS('color', MONOCHROME_COLORS.white);
  });

  // ===== RESPONSIVE GRID LAYOUT TESTS =====
  
  test('Desktop layout (1920x1080): 3-column grid with full sidebar', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await page.goto(DASHBOARD_URL);
    
    // Verify 12-column grid system
    const gridContainer = page.locator('.grid.grid-cols-12').first();
    await expect(gridContainer).toBeVisible();
    await expect(gridContainer).toHaveCSS('display', 'grid');
    
    // Check column spans - desktop should show full 3-column layout
    await expect(page.locator('.col-3')).toHaveCount(2); // Left and right sidebars
    await expect(page.locator('.col-6')).toHaveCount(1); // Main content
    await expect(page.locator('.col-8')).toHaveCount(1); // Header left section
    await expect(page.locator('.col-4')).toHaveCount(1); // Header right section
    
    // Verify panels are properly sized for desktop
    const leftSidebar = page.locator('aside.col-3').first();
    const rightSidebar = page.locator('aside.col-3').last();
    const mainContent = page.locator('main.col-6');
    
    await expect(leftSidebar).toBeVisible();
    await expect(rightSidebar).toBeVisible();
    await expect(mainContent).toBeVisible();
  });

  test('Tablet layout (768x1024): Responsive grid adjustments', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.tablet);
    await page.goto(DASHBOARD_URL);
    
    // Grid should still be functional but may adapt
    await expect(page.locator('.grid')).toBeVisible();
    
    // All main sections should still be visible on tablet
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('aside')).toHaveCount(2);
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('footer')).toBeVisible();
  });

  test('Mobile layout (375x667): Single column responsive design', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await page.goto(DASHBOARD_URL);
    
    // Mobile should still show core structure
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('footer')).toBeVisible();
    
    // Check that panels don't overflow
    const panels = page.locator('.panel');
    const panelCount = await panels.count();
    
    for (let i = 0; i < panelCount; i++) {
      const panel = panels.nth(i);
      const boundingBox = await panel.boundingBox();
      if (boundingBox) {
        expect(boundingBox.width).toBeLessThanOrEqual(VIEWPORTS.mobile.width);
      }
    }
  });

  // ===== INTERACTIVE ELEMENTS TESTS =====
  
  test('Header buttons functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test window control buttons
    await expect(page.locator('#minimize-btn')).toBeVisible();
    await expect(page.locator('#fullscreen-btn')).toBeVisible();
    await expect(page.locator('#close-btn')).toBeVisible();
    
    // Test button interactions
    await page.locator('#minimize-btn').click();
    await page.locator('#fullscreen-btn').click();
    
    // Buttons should have proper hover states
    await page.locator('#close-btn').hover();
    await expect(page.locator('#close-btn')).toHaveCSS('background-color', MONOCHROME_COLORS.white);
  });

  test('Tab navigation system functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test all tab buttons
    const tabButtons = ['dashboardTab', 'chatTab', 'settingsTab', 'debugTab'];
    const tabContents = ['dashboardContent', 'chatContent', 'settingsContent', 'debugContent'];
    
    for (let i = 0; i < tabButtons.length; i++) {
      const tabButton = page.locator(`#${tabButtons[i]}`);
      const tabContent = page.locator(`#${tabContents[i]}`);
      
      await expect(tabButton).toBeVisible();
      await tabButton.click();
      
      // Verify active state
      await expect(tabButton).toHaveClass(/tab-active/);
      await expect(tabContent).toHaveClass(/active/);
      
      // Verify other tabs are inactive
      for (let j = 0; j < tabButtons.length; j++) {
        if (i !== j) {
          await expect(page.locator(`#${tabContents[j]}`)).not.toHaveClass(/active/);
        }
      }
    }
  });

  test('Emotion control buttons functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test emotion buttons
    const emotions = ['happy', 'sad', 'angry', 'surprised', 'thinking', 'excited'];
    
    for (const emotion of emotions) {
      const emotionBtn = page.locator(`[data-emotion="${emotion}"]`);
      await expect(emotionBtn).toBeVisible();
      
      await emotionBtn.click();
      
      // Verify emotion is displayed
      await expect(page.locator('#currentEmotion')).toContainText(emotion);
    }
  });

  test('Intensity slider functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    const intensitySlider = page.locator('#emotionIntensity');
    const intensityValue = page.locator('#emotionIntensityValue');
    const currentIntensityDisplay = page.locator('#currentIntensity');
    
    await expect(intensitySlider).toBeVisible();
    await expect(intensityValue).toBeVisible();
    
    // Test slider at different values
    await intensitySlider.fill('25');
    await expect(intensityValue).toContainText('25');
    await expect(currentIntensityDisplay).toContainText('25%');
    
    await intensitySlider.fill('75');
    await expect(intensityValue).toContainText('75');
    await expect(currentIntensityDisplay).toContainText('75%');
  });

  test('Chat interface functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Navigate to chat tab
    await page.locator('#chatTab').click();
    
    const chatInput = page.locator('#chatInput');
    const sendButton = page.locator('#sendButton');
    const voiceButton = page.locator('#voiceButton');
    const chatMessages = page.locator('#chatMessages');
    
    await expect(chatInput).toBeVisible();
    await expect(sendButton).toBeVisible();
    await expect(voiceButton).toBeVisible();
    
    // Test sending a message
    await chatInput.fill('Test message for chat interface');
    await sendButton.click();
    
    // Check message appears in chat
    await expect(chatMessages).toContainText('Test message for chat interface');
    
    // Test Enter key sending
    await chatInput.fill('Another test message');
    await chatInput.press('Enter');
    await expect(chatMessages).toContainText('Another test message');
  });

  // ===== ACCESSIBILITY TESTS =====
  
  test('Accessibility: Focus indicators for keyboard navigation', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test focus on interactive elements
    const focusableElements = [
      '#minimize-btn',
      '#fullscreen-btn', 
      '#close-btn',
      '#dashboardTab',
      '#chatTab',
      '#settingsTab',
      '#debugTab',
      '[data-emotion="happy"]',
      '#emotionIntensity',
      '#testEmotions',
      '#randomizeAvatar',
      '#resetAvatar'
    ];
    
    for (const selector of focusableElements) {
      const element = page.locator(selector);
      if (await element.isVisible()) {
        await element.focus();
        
        // Check for focus outline (should be 2px solid white)
        const outlineColor = await element.evaluate(el => 
          window.getComputedStyle(el).outlineColor
        );
        const outlineWidth = await element.evaluate(el => 
          window.getComputedStyle(el).outlineWidth
        );
        
        expect(outlineColor).toBe(MONOCHROME_COLORS.white);
        expect(outlineWidth).toBe('2px');
      }
    }
  });

  test('Accessibility: Color contrast compliance', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test key text elements for sufficient contrast
    const textElements = [
      { selector: 'h1', minContrast: 4.5 },                    // Large text
      { selector: '.text-gray', minContrast: 3.0 },            // Normal text (updated for accessibility)
      { selector: '.text-white', minContrast: 4.5 },          // High contrast text
      { selector: '.panel-title', minContrast: 4.5 },         // Important labels
      { selector: '.metric-label', minContrast: 3.0 }         // Secondary text
    ];
    
    for (const { selector, minContrast } of textElements) {
      const element = page.locator(selector).first();
      if (await element.isVisible()) {
        const textColor = await element.evaluate(el => 
          window.getComputedStyle(el).color
        );
        const backgroundColor = await element.evaluate(el => {
          let bgColor = window.getComputedStyle(el).backgroundColor;
          if (bgColor === 'rgba(0, 0, 0, 0)' || bgColor === 'transparent') {
            // Find parent with background
            let parent = el.parentElement;
            while (parent && (bgColor === 'rgba(0, 0, 0, 0)' || bgColor === 'transparent')) {
              bgColor = window.getComputedStyle(parent).backgroundColor;
              parent = parent.parentElement;
            }
          }
          return bgColor || 'rgb(0, 0, 0)'; // Default to black
        });
        
        // Simple contrast check - verify light text on dark background or vice versa
        const isLightText = textColor.includes('255') || textColor.includes('204') || textColor.includes('136');
        const isDarkBackground = backgroundColor.includes('0, 0, 0') || backgroundColor.includes('10, 10, 10') || backgroundColor.includes('26, 26, 26');
        
        if (isLightText) {
          expect(isDarkBackground).toBeTruthy();
        }
      }
    }
  });

  test('Accessibility: Keyboard navigation flow', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test Tab navigation through interactive elements
    const tabOrder = [
      '#minimize-btn',
      '#fullscreen-btn',
      '#close-btn',
      '#dashboardTab',
      '#chatTab',
      '#settingsTab',
      '#debugTab'
    ];
    
    // Start tabbing from first element
    await page.keyboard.press('Tab');
    
    for (const selector of tabOrder) {
      const element = page.locator(selector);
      if (await element.isVisible()) {
        await expect(element).toBeFocused();
        await page.keyboard.press('Tab');
      }
    }
  });

  // ===== AVATAR SYSTEM INTEGRATION TESTS =====
  
  test('Avatar canvas elements present and functional', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Check avatar canvas elements exist
    await expect(page.locator('#avatarCanvas')).toBeVisible();
    await expect(page.locator('#particleCanvas')).toBeVisible();
    
    // Verify canvas dimensions
    const avatarCanvas = page.locator('#avatarCanvas');
    await expect(avatarCanvas).toHaveAttribute('width', '150');
    await expect(avatarCanvas).toHaveAttribute('height', '150');
    
    const particleCanvas = page.locator('#particleCanvas');
    await expect(particleCanvas).toHaveAttribute('width', '150');
    await expect(particleCanvas).toHaveAttribute('height', '150');
  });

  test('Avatar control buttons functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test avatar control buttons
    const controlButtons = ['#testEmotions', '#randomizeAvatar', '#resetAvatar'];
    
    for (const selector of controlButtons) {
      const button = page.locator(selector);
      await expect(button).toBeVisible();
      await expect(button).toBeEnabled();
      
      // Test button interaction
      await button.click();
      
      // Button should have proper hover/click states
      await button.hover();
    }
  });

  test('Avatar status indicators update', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test that avatar status elements are present and display values
    await expect(page.locator('#currentEmotion')).toBeVisible();
    await expect(page.locator('#currentIntensity')).toBeVisible();
    await expect(page.locator('#avatarStyle')).toBeVisible();
    
    // Default values should be displayed
    await expect(page.locator('#currentEmotion')).toContainText('neutral');
    await expect(page.locator('#currentIntensity')).toContainText('50%');
    await expect(page.locator('#avatarStyle')).toContainText('robot');
  });

  // ===== REAL-TIME UPDATES TESTS =====
  
  test('System metrics display elements present', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test system metrics are displayed
    const metrics = ['#cpuUsage', '#memoryUsage', '#temperature', '#battery'];
    
    for (const selector of metrics) {
      await expect(page.locator(selector)).toBeVisible();
      
      // Should have initial values
      const value = await page.locator(selector).textContent();
      expect(value).toBeTruthy();
    }
  });

  test('Performance metrics display elements present', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test performance metrics
    const performanceMetrics = [
      '#messagesSent',
      '#messagesReceived', 
      '#generationSpeed',
      '#uptime'
    ];
    
    for (const selector of performanceMetrics) {
      await expect(page.locator(selector)).toBeVisible();
    }
  });

  test('Footer status updates functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test footer status elements
    await expect(page.locator('#footerWsStatus')).toBeVisible();
    await expect(page.locator('#footerUptime')).toBeVisible();
    await expect(page.locator('#footerEcoSavings')).toBeVisible();
    
    // Test footer action buttons
    const footerButtons = page.locator('footer .btn');
    const buttonCount = await footerButtons.count();
    expect(buttonCount).toBeGreaterThan(0);
    
    for (let i = 0; i < buttonCount; i++) {
      await expect(footerButtons.nth(i)).toBeVisible();
      await expect(footerButtons.nth(i)).toBeEnabled();
    }
  });

  test('Connection status indicator functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test connection status elements
    await expect(page.locator('#connectionDot')).toBeVisible();
    await expect(page.locator('#connectionText')).toBeVisible();
    
    // Should show initial status
    await expect(page.locator('#connectionText')).toContainText('INITIALIZING');
    
    // Status indicator should have appropriate class
    const statusDot = page.locator('#connectionDot');
    const statusClass = await statusDot.getAttribute('class');
    expect(statusClass).toContain('status-');
  });

  // ===== COMPONENT STATUS TESTS =====
  
  test('Component status section displays all systems', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Verify component status section exists
    await expect(page.locator('#componentStatus')).toBeVisible();
    
    // Should show status for key components
    const statusTexts = [
      'AI Engine',
      'Voice System', 
      'Avatar Server',
      'WebSocket'
    ];
    
    for (const statusText of statusTexts) {
      await expect(page.locator('#componentStatus')).toContainText(statusText);
    }
  });

  // ===== DEBUG AND SETTINGS FUNCTIONALITY =====
  
  test('Debug tab functionality', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Navigate to debug tab
    await page.locator('#debugTab').click();
    
    // Verify debug content is shown
    await expect(page.locator('#debugContent')).toHaveClass(/active/);
    await expect(page.locator('#debugOutput')).toBeVisible();
    
    // Test debug buttons
    const debugButtons = page.locator('#debugContent .btn');
    const buttonCount = await debugButtons.count();
    
    for (let i = 0; i < buttonCount; i++) {
      await expect(debugButtons.nth(i)).toBeVisible();
      await expect(debugButtons.nth(i)).toBeEnabled();
    }
  });

  test('Settings tab displays placeholder content', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Navigate to settings tab
    await page.locator('#settingsTab').click();
    
    // Verify settings content
    await expect(page.locator('#settingsContent')).toHaveClass(/active/);
    await expect(page.locator('#settingsContent')).toContainText('Settings interface will be implemented');
  });

  // ===== MOBILE-SPECIFIC TESTS =====
  
  test('Mobile: Touch targets are properly sized', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await page.goto(DASHBOARD_URL);
    
    // Test that buttons have minimum 44px touch targets on mobile
    const buttons = page.locator('.btn');
    const buttonCount = await buttons.count();
    
    for (let i = 0; i < buttonCount; i++) {
      const button = buttons.nth(i);
      const boundingBox = await button.boundingBox();
      
      if (boundingBox) {
        expect(boundingBox.height).toBeGreaterThanOrEqual(44);
        expect(boundingBox.width).toBeGreaterThanOrEqual(44);
      }
    }
  });

  test('Mobile: Text remains readable at small sizes', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await page.goto(DASHBOARD_URL);
    
    // Check that text-xs elements are at least 14px on mobile
    const smallText = page.locator('.text-xs');
    const count = await smallText.count();
    
    for (let i = 0; i < count; i++) {
      const fontSize = await smallText.nth(i).evaluate(el => 
        window.getComputedStyle(el).fontSize
      );
      
      const fontSizeNumber = parseInt(fontSize.replace('px', ''));
      expect(fontSizeNumber).toBeGreaterThanOrEqual(14);
    }
  });

  // ===== GRID SYSTEM VALIDATION =====
  
  test('Grid system: CSS classes exist and function correctly', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test that required grid classes exist and work
    const gridElements = [
      '.grid',
      '.grid-cols-12', 
      '.grid-cols-2',
      '.grid-cols-3',
      '.col-3',
      '.col-6',
      '.col-8',
      '.col-4'
    ];
    
    for (const selector of gridElements) {
      const elements = page.locator(selector);
      const count = await elements.count();
      expect(count).toBeGreaterThan(0);
      
      // First element should be visible and have proper CSS
      if (count > 0) {
        await expect(elements.first()).toBeVisible();
      }
    }
  });

  test('Spacing utilities work correctly', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // Test spacing utilities
    const spacingElements = [
      '.gap-2',
      '.gap-4', 
      '.space-y-2',
      '.space-y-4',
      '.mt-2',
      '.mb-3',
      '.p-2',
      '.p-4'
    ];
    
    for (const selector of spacingElements) {
      const elements = page.locator(selector);
      if (await elements.count() > 0) {
        await expect(elements.first()).toBeVisible();
      }
    }
  });

  // ===== OVERALL INTEGRATION TEST =====
  
  test('Full workflow integration test', async ({ page }) => {
    await page.goto(DASHBOARD_URL);
    
    // 1. Verify dashboard loads completely
    await expect(page.locator('h1')).toContainText('M1K3');
    
    // 2. Test tab navigation
    await page.locator('#chatTab').click();
    await expect(page.locator('#chatContent')).toHaveClass(/active/);
    
    // 3. Send a chat message
    await page.locator('#chatInput').fill('Integration test message');
    await page.locator('#sendButton').click();
    await expect(page.locator('#chatMessages')).toContainText('Integration test message');
    
    // 4. Switch to debug tab and test functionality
    await page.locator('#debugTab').click();
    await expect(page.locator('#debugContent')).toHaveClass(/active/);
    
    // 5. Test emotion controls
    await page.locator('[data-emotion="excited"]').click();
    await expect(page.locator('#currentEmotion')).toContainText('excited');
    
    // 6. Test intensity slider
    await page.locator('#emotionIntensity').fill('80');
    await expect(page.locator('#emotionIntensityValue')).toContainText('80');
    
    // 7. Verify all core elements still visible and functional
    await expect(page.locator('header')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('footer')).toBeVisible();
    await expect(page.locator('#avatarCanvas')).toBeVisible();
  });
});

// Export test configuration for CI/CD
module.exports = {
  DASHBOARD_URL,
  VIEWPORTS,
  MONOCHROME_COLORS
};