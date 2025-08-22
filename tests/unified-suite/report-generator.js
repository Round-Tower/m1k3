/**
 * M1K3 Brand-Aligned HTML Report Generator
 * Creates stunning test reports using the pure black design system
 */

const fs = require('fs').promises;
const path = require('path');

class ReportGenerator {
    constructor(options = {}) {
        this.outputDir = options.outputDir || path.join(__dirname, 'output/reports');
        this.templateDir = path.join(__dirname, 'templates');
        this.screenshotDir = path.resolve(__dirname, '../screenshots');
        this.rootDir = options.rootDir || path.resolve(__dirname, '../..');
        this.brandColors = {
            primary: '#000000',
            accent: '#E25303',
            success: '#10b981',
            error: '#ef4444',
            warning: '#f59e0b',
            info: '#3b82f6'
        };
    }

    /**
     * Generate comprehensive HTML report
     */
    async generateReport(testResults) {
        console.log('🎨 Generating M1K3-branded test report...');

        const reportData = this.processTestResults(testResults);
        
        // Process screenshots and user journeys
        console.log('📸 Processing screenshots and user journeys...');
        const screenshots = await this.processScreenshots();
        const userJourneys = await this.processUserJourneys();
        
        reportData.screenshots = screenshots;
        reportData.userJourneys = userJourneys;
        
        const html = await this.buildHTMLReport(reportData);
        
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `m1k3-test-report-${timestamp}.html`;
        const outputPath = path.join(this.outputDir, filename);
        
        await fs.writeFile(outputPath, html);
        
        console.log(`✨ Report generated: ${outputPath}`);
        return outputPath;
    }

    /**
     * Process test results for report generation
     */
    processTestResults(results) {
        const { summary, tests, categories, performance } = results;
        
        return {
            ...results,
            analytics: {
                success_rate: ((summary.passed / summary.total) * 100).toFixed(1),
                failure_rate: ((summary.failed / summary.total) * 100).toFixed(1),
                avg_duration: Math.round(performance.avg_test_duration || 0),
                total_duration_formatted: this.formatDuration(summary.duration),
                categories_summary: this.generateCategorySummary(categories),
                trend_indicators: this.generateTrendIndicators(tests),
                performance_metrics: this.generatePerformanceMetrics(performance)
            },
            visualizations: {
                category_chart: this.generateCategoryChart(categories),
                timeline_chart: this.generateTimelineChart(tests),
                performance_chart: this.generatePerformanceChart(performance)
            }
        };
    }

    /**
     * Process screenshots from test directory
     */
    async processScreenshots() {
        try {
            const screenshots = {
                baseline: [],
                current: [],
                diff: []
            };

            // Process baseline screenshots
            try {
                const baselineDir = path.join(this.screenshotDir, 'baseline');
                const baselineFiles = await fs.readdir(baselineDir);
                
                for (const file of baselineFiles) {
                    if (file.endsWith('.png')) {
                        const fullPath = path.join(baselineDir, file);
                        const relativePath = path.relative(this.outputDir, fullPath);
                        screenshots.baseline.push({
                            name: file.replace('.png', ''),
                            filename: file,
                            path: relativePath,
                            category: this.categorizeScreenshot(file),
                            viewport: this.extractViewport(file),
                            route: this.extractRoute(file)
                        });
                    }
                }
            } catch (error) {
                console.warn('No baseline screenshots found');
            }

            // Process current screenshots
            try {
                const currentDir = path.join(this.screenshotDir, 'current');
                const currentFiles = await fs.readdir(currentDir);
                
                for (const file of currentFiles) {
                    if (file.endsWith('.png')) {
                        const fullPath = path.join(currentDir, file);
                        const relativePath = path.relative(this.outputDir, fullPath);
                        screenshots.current.push({
                            name: file.replace('.png', ''),
                            filename: file,
                            path: relativePath,
                            category: this.categorizeScreenshot(file),
                            viewport: this.extractViewport(file),
                            route: this.extractRoute(file)
                        });
                    }
                }
            } catch (error) {
                console.warn('No current screenshots found');
            }

            // Process diff screenshots if they exist
            try {
                const diffDir = path.join(this.screenshotDir, 'diff');
                const diffFiles = await fs.readdir(diffDir);
                
                for (const file of diffFiles) {
                    if (file.endsWith('.png')) {
                        const fullPath = path.join(diffDir, file);
                        const relativePath = path.relative(this.outputDir, fullPath);
                        screenshots.diff.push({
                            name: file.replace('.png', ''),
                            filename: file,
                            path: relativePath,
                            category: this.categorizeScreenshot(file),
                            viewport: this.extractViewport(file),
                            route: this.extractRoute(file)
                        });
                    }
                }
            } catch (error) {
                // Diff directory might be empty
            }

            return screenshots;
        } catch (error) {
            console.warn('Error processing screenshots:', error.message);
            return { baseline: [], current: [], diff: [] };
        }
    }

    /**
     * Process user journeys from test configuration
     */
    async processUserJourneys() {
        try {
            // Define user journeys based on screenshot analysis
            const journeys = [
                {
                    name: 'new-user-onboarding',
                    title: 'New User Onboarding',
                    description: 'First-time user experience through core features',
                    steps: [
                        { name: 'app-landing', title: 'Landing Page', description: 'User arrives at main application' },
                        { name: 'app-home', title: 'Home Dashboard', description: 'User views home dashboard' },
                        { name: 'app-dashboard', title: 'Avatar Dashboard', description: 'User explores avatar system' },
                        { name: 'app-chat', title: 'Chat Interface', description: 'User interacts with AI chat' }
                    ]
                },
                {
                    name: 'developer-workflow',
                    title: 'Developer Workflow', 
                    description: 'Developer testing and debugging experience',
                    steps: [
                        { name: 'app-landing', title: 'Developer Entry', description: 'Developer accesses application' },
                        { name: 'app-dashboard', title: 'System Dashboard', description: 'Developer views system status' },
                        { name: 'app-chat', title: 'API Testing', description: 'Developer tests chat functionality' }
                    ]
                },
                {
                    name: 'responsive-design',
                    title: 'Responsive Design Validation',
                    description: 'Multi-device experience validation',
                    steps: [
                        { name: 'mobile-experience', title: 'Mobile Views', description: 'Mobile-optimized interfaces' },
                        { name: 'tablet-experience', title: 'Tablet Views', description: 'Tablet-optimized layouts' },
                        { name: 'desktop-experience', title: 'Desktop Views', description: 'Desktop full-featured experience' }
                    ]
                }
            ];

            // Match screenshots to journey steps
            const screenshots = await this.processScreenshots();
            
            for (const journey of journeys) {
                for (const step of journey.steps) {
                    step.screenshots = {
                        desktop: [],
                        tablet: [],
                        mobile: []
                    };

                    // Match screenshots to this step
                    ['baseline', 'current'].forEach(type => {
                        screenshots[type].forEach(screenshot => {
                            const matchesStep = this.matchesJourneyStep(screenshot, step);
                            if (matchesStep) {
                                step.screenshots[screenshot.viewport].push({
                                    ...screenshot,
                                    type: type
                                });
                            }
                        });
                    });
                }
            }

            return journeys;
        } catch (error) {
            console.warn('Error processing user journeys:', error.message);
            return [];
        }
    }

    /**
     * Categorize screenshot by filename
     */
    categorizeScreenshot(filename) {
        if (filename.includes('app-')) return 'app';
        if (filename.includes('pwa-')) return 'pwa';
        if (filename.includes('avatar-')) return 'avatar';
        if (filename.includes('chat-')) return 'chat';
        if (filename.includes('dashboard-')) return 'dashboard';
        return 'other';
    }

    /**
     * Extract viewport from filename
     */
    extractViewport(filename) {
        if (filename.includes('-mobile')) return 'mobile';
        if (filename.includes('-tablet')) return 'tablet';
        if (filename.includes('-desktop')) return 'desktop';
        return 'desktop'; // default
    }

    /**
     * Extract route from filename
     */
    extractRoute(filename) {
        const parts = filename.replace('.png', '').split('-');
        if (parts.length >= 2) {
            return parts.slice(0, -1).join('-'); // Remove viewport suffix
        }
        return filename.replace('.png', '');
    }

    /**
     * Check if screenshot matches journey step
     */
    matchesJourneyStep(screenshot, step) {
        const stepName = step.name.toLowerCase();
        const screenshotName = screenshot.name.toLowerCase();
        
        // Direct name matching
        if (screenshotName.includes(stepName)) return true;
        
        // Route-based matching
        if (stepName.includes('mobile') && screenshot.viewport === 'mobile') return true;
        if (stepName.includes('tablet') && screenshot.viewport === 'tablet') return true;
        if (stepName.includes('desktop') && screenshot.viewport === 'desktop') return true;
        
        // Content-based matching
        if (stepName.includes('landing') && screenshotName.includes('landing')) return true;
        if (stepName.includes('home') && screenshotName.includes('home')) return true;
        if (stepName.includes('dashboard') && screenshotName.includes('dashboard')) return true;
        if (stepName.includes('chat') && screenshotName.includes('chat')) return true;
        
        return false;
    }

    /**
     * Build complete HTML report
     */
    async buildHTMLReport(data) {
        const styles = this.generateStyles();
        const scripts = this.generateScripts();
        
        return `
<!DOCTYPE html>
<html lang="en" class="theme-pure-black">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>M1K3 Test Report - ${new Date().toLocaleDateString()}</title>
    <style>${styles}</style>
</head>
<body>
    ${this.generateHeader(data)}
    ${this.generateExecutiveSummary(data)}
    ${this.generateCategoryBreakdown(data)}
    ${this.generateUserJourneysSection(data)}
    ${this.generateScreenshotGallery(data)}
    ${this.generatePerformanceSection(data)}
    ${this.generateTestResults(data)}
    ${this.generateSecuritySection(data)}
    ${this.generateFooter(data)}
    ${this.generateFloatingParticles()}
    
    <script>${scripts}</script>
</body>
</html>`;
    }

    /**
     * Generate M1K3 branded CSS styles
     */
    generateStyles() {
        return `
        /* M1K3 Pure Black Design System */
        :root {
            --bg-primary: #000000;
            --bg-secondary: rgba(255, 255, 255, 0.02);
            --bg-tertiary: rgba(255, 255, 255, 0.04);
            --bg-elevated: rgba(255, 255, 255, 0.08);
            --bg-glass: rgba(255, 255, 255, 0.03);
            
            --text-primary: rgba(255, 255, 255, 0.98);
            --text-secondary: rgba(255, 255, 255, 0.75);
            --text-muted: rgba(255, 255, 255, 0.45);
            
            --accent-m1k3: #E25303;
            --success: #10b981;
            --error: #ef4444;
            --warning: #f59e0b;
            --info: #3b82f6;
            
            --border-subtle: rgba(255, 255, 255, 0.06);
            --border-light: rgba(255, 255, 255, 0.10);
            --border-medium: rgba(255, 255, 255, 0.15);
            
            --shadow-subtle: 0 2px 8px rgba(0, 0, 0, 0.4);
            --shadow-medium: 0 4px 16px rgba(0, 0, 0, 0.6);
            --shadow-glow: 0 0 20px rgba(255, 255, 255, 0.1);
            
            --space-xs: 4px;
            --space-sm: 8px;
            --space-md: 16px;
            --space-lg: 24px;
            --space-xl: 32px;
            --space-2xl: 48px;
            
            --radius-sm: 4px;
            --radius-md: 8px;
            --radius-lg: 12px;
            --radius-xl: 16px;
            
            --duration-fast: 150ms;
            --duration-normal: 250ms;
            --duration-slow: 400ms;
        }

        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
            background: var(--bg-primary);
            color: var(--text-primary);
            line-height: 1.6;
            overflow-x: hidden;
            scroll-behavior: smooth;
        }

        /* Header */
        .header {
            background: var(--bg-secondary);
            border-bottom: 1px solid var(--border-subtle);
            padding: var(--space-xl) var(--space-lg);
            text-align: center;
            position: relative;
            overflow: hidden;
        }

        .header::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: linear-gradient(135deg, 
                rgba(226, 83, 3, 0.1) 0%, 
                transparent 50%, 
                rgba(226, 83, 3, 0.05) 100%);
            pointer-events: none;
        }

        .header-content {
            position: relative;
            z-index: 1;
        }

        .logo {
            display: inline-flex;
            align-items: center;
            gap: var(--space-sm);
            margin-bottom: var(--space-md);
        }

        .logo-text {
            font-size: 2.5rem;
            font-weight: 700;
            background: linear-gradient(135deg, var(--text-primary), var(--accent-m1k3));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .logo-badge {
            background: var(--accent-m1k3);
            color: white;
            padding: var(--space-xs) var(--space-sm);
            border-radius: var(--radius-sm);
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .header-subtitle {
            color: var(--text-secondary);
            font-size: 1.125rem;
            margin-bottom: var(--space-lg);
        }

        .header-meta {
            display: flex;
            justify-content: center;
            gap: var(--space-xl);
            font-size: 0.875rem;
            color: var(--text-muted);
        }

        /* Executive Summary */
        .executive-summary {
            padding: var(--space-2xl) var(--space-lg);
            background: var(--bg-secondary);
        }

        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: var(--space-lg);
            max-width: 1200px;
            margin: 0 auto;
        }

        .metric-card {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            padding: var(--space-xl);
            text-align: center;
            transition: all var(--duration-normal);
            position: relative;
            overflow: hidden;
        }

        .metric-card:hover {
            transform: translateY(-2px);
            box-shadow: var(--shadow-medium);
            border-color: var(--border-light);
        }

        .metric-card.success {
            border-left: 4px solid var(--success);
        }

        .metric-card.error {
            border-left: 4px solid var(--error);
        }

        .metric-card.info {
            border-left: 4px solid var(--info);
        }

        .metric-number {
            font-size: 3rem;
            font-weight: 700;
            margin-bottom: var(--space-sm);
            display: block;
        }

        .metric-number.success {
            color: var(--success);
        }

        .metric-number.error {
            color: var(--error);
        }

        .metric-number.info {
            color: var(--info);
        }

        .metric-label {
            color: var(--text-secondary);
            font-size: 0.875rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: var(--space-xs);
        }

        .metric-description {
            color: var(--text-muted);
            font-size: 0.75rem;
        }

        /* Section Headers */
        .section {
            padding: var(--space-2xl) var(--space-lg);
        }

        .section-header {
            max-width: 1200px;
            margin: 0 auto var(--space-xl);
            text-align: center;
        }

        .section-title {
            font-size: 2rem;
            font-weight: 700;
            margin-bottom: var(--space-sm);
            color: var(--text-primary);
        }

        .section-subtitle {
            color: var(--text-secondary);
            font-size: 1.125rem;
        }

        /* Category Breakdown */
        .category-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: var(--space-lg);
            max-width: 1200px;
            margin: 0 auto;
        }

        .category-card {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            padding: var(--space-lg);
            transition: all var(--duration-normal);
        }

        .category-card:hover {
            border-color: var(--border-medium);
            box-shadow: var(--shadow-subtle);
        }

        .category-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--space-md);
        }

        .category-name {
            font-size: 1.25rem;
            font-weight: 600;
            text-transform: capitalize;
        }

        .category-badge {
            background: var(--bg-elevated);
            color: var(--text-secondary);
            padding: var(--space-xs) var(--space-sm);
            border-radius: var(--radius-md);
            font-size: 0.75rem;
            font-weight: 500;
        }

        .category-stats {
            display: grid;
            grid-template-columns: 1fr 1fr 1fr;
            gap: var(--space-sm);
            margin-bottom: var(--space-md);
        }

        .stat-item {
            text-align: center;
            padding: var(--space-sm);
            background: var(--bg-elevated);
            border-radius: var(--radius-sm);
        }

        .stat-value {
            font-size: 1.25rem;
            font-weight: 600;
            margin-bottom: var(--space-xs);
        }

        .stat-label {
            font-size: 0.625rem;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .progress-bar {
            width: 100%;
            height: 8px;
            background: var(--bg-elevated);
            border-radius: var(--radius-sm);
            overflow: hidden;
        }

        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, var(--success), var(--accent-m1k3));
            transition: width var(--duration-slow);
        }

        /* Test Results Table */
        .test-results {
            max-width: 1200px;
            margin: 0 auto;
        }

        .results-controls {
            display: flex;
            gap: var(--space-sm);
            margin-bottom: var(--space-lg);
            flex-wrap: wrap;
        }

        .filter-btn {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-subtle);
            color: var(--text-secondary);
            padding: var(--space-sm) var(--space-md);
            border-radius: var(--radius-md);
            cursor: pointer;
            transition: all var(--duration-fast);
            font-size: 0.875rem;
        }

        .filter-btn:hover,
        .filter-btn.active {
            background: var(--accent-m1k3);
            color: white;
            border-color: var(--accent-m1k3);
        }

        .test-table {
            width: 100%;
            background: var(--bg-tertiary);
            border-radius: var(--radius-lg);
            overflow: hidden;
            border: 1px solid var(--border-subtle);
        }

        .test-table th,
        .test-table td {
            padding: var(--space-md);
            text-align: left;
            border-bottom: 1px solid var(--border-subtle);
        }

        .test-table th {
            background: var(--bg-elevated);
            font-weight: 600;
            font-size: 0.875rem;
            color: var(--text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .test-table tr:hover {
            background: var(--bg-elevated);
        }

        .status-badge {
            padding: var(--space-xs) var(--space-sm);
            border-radius: var(--radius-sm);
            font-size: 0.75rem;
            font-weight: 500;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .status-passed {
            background: rgba(16, 185, 129, 0.2);
            color: var(--success);
        }

        .status-failed {
            background: rgba(239, 68, 68, 0.2);
            color: var(--error);
        }

        .status-skipped {
            background: rgba(249, 158, 11, 0.2);
            color: var(--warning);
        }

        /* Floating Particles */
        .particles {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            pointer-events: none;
            z-index: -1;
        }

        .particle {
            position: absolute;
            width: 2px;
            height: 2px;
            background: var(--text-muted);
            border-radius: 50%;
            animation: float 15s infinite linear;
        }

        @keyframes float {
            0% {
                transform: translateY(100vh) scale(0);
                opacity: 0;
            }
            10% {
                opacity: 0.4;
            }
            50% {
                transform: translateY(-50vh) scale(1);
                opacity: 0.2;
            }
            90% {
                opacity: 0.4;
            }
            100% {
                transform: translateY(-100vh) scale(0);
                opacity: 0;
            }
        }

        /* Animations */
        .fade-in {
            animation: fadeIn var(--duration-slow) ease-out;
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(20px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .slide-in {
            animation: slideIn var(--duration-normal) ease-out;
        }

        @keyframes slideIn {
            from { transform: translateX(-20px); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }

        /* Responsive Design */
        @media (max-width: 768px) {
            .header-meta {
                flex-direction: column;
                gap: var(--space-sm);
            }

            .summary-grid {
                grid-template-columns: 1fr;
                gap: var(--space-md);
            }

            .category-grid {
                grid-template-columns: 1fr;
            }

            .results-controls {
                justify-content: center;
            }

            .test-table {
                font-size: 0.875rem;
            }

            .test-table th,
            .test-table td {
                padding: var(--space-sm);
            }
        }

        /* Dark theme scrollbars */
        ::-webkit-scrollbar {
            width: 8px;
        }

        ::-webkit-scrollbar-track {
            background: var(--bg-secondary);
        }

        ::-webkit-scrollbar-thumb {
            background: var(--bg-elevated);
            border-radius: var(--radius-sm);
        }

        ::-webkit-scrollbar-thumb:hover {
            background: var(--border-light);
        }

        /* User Journeys & Screenshots */
        .user-journeys {
            padding: var(--space-2xl) var(--space-lg);
            background: var(--bg-primary);
        }

        .journey-card {
            background: var(--bg-secondary);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-lg);
            margin-bottom: var(--space-xl);
            overflow: hidden;
        }

        .journey-header {
            padding: var(--space-xl);
            border-bottom: 1px solid var(--border-subtle);
        }

        .journey-title {
            font-size: 1.5rem;
            font-weight: 600;
            margin: 0 0 var(--space-sm) 0;
            color: var(--text-primary);
        }

        .journey-description {
            color: var(--text-secondary);
            margin: 0;
        }

        .journey-steps {
            display: grid;
            gap: var(--space-lg);
            padding: var(--space-xl);
        }

        .journey-step {
            background: var(--bg-tertiary);
            border-radius: var(--radius-md);
            padding: var(--space-lg);
        }

        .step-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--space-md);
        }

        .step-title {
            font-size: 1.125rem;
            font-weight: 500;
            color: var(--text-primary);
            margin: 0;
        }

        .step-description {
            color: var(--text-secondary);
            margin: 0 0 var(--space-lg) 0;
        }

        .viewport-tabs {
            display: flex;
            gap: var(--space-sm);
            margin-bottom: var(--space-lg);
        }

        .viewport-tab {
            padding: var(--space-sm) var(--space-md);
            background: var(--bg-elevated);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-sm);
            color: var(--text-secondary);
            cursor: pointer;
            transition: all var(--duration-fast);
        }

        .viewport-tab.active {
            background: var(--accent-m1k3);
            color: #ffffff;
            border-color: var(--accent-m1k3);
        }

        .screenshot-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: var(--space-lg);
        }

        .screenshot-item {
            background: var(--bg-elevated);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-md);
            overflow: hidden;
            transition: all var(--duration-normal);
        }

        .screenshot-item:hover {
            transform: translateY(-2px);
            box-shadow: var(--shadow-medium);
        }

        .screenshot-image {
            width: 100%;
            height: auto;
            display: block;
            cursor: pointer;
        }

        .screenshot-meta {
            padding: var(--space-md);
        }

        .screenshot-name {
            font-size: 0.875rem;
            font-weight: 500;
            color: var(--text-primary);
            margin: 0 0 var(--space-xs) 0;
        }

        .screenshot-type {
            font-size: 0.75rem;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .screenshot-type.baseline {
            color: var(--info);
        }

        .screenshot-type.current {
            color: var(--success);
        }

        .screenshot-type.diff {
            color: var(--warning);
        }

        /* Screenshot Modal */
        .screenshot-modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.9);
            z-index: 1000;
            cursor: pointer;
        }

        .screenshot-modal.active {
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .screenshot-modal img {
            max-width: 90%;
            max-height: 90%;
            border-radius: var(--radius-md);
            box-shadow: var(--shadow-large);
        }

        /* Screenshot Gallery */
        .screenshot-gallery {
            padding: var(--space-2xl) var(--space-lg);
            background: var(--bg-secondary);
        }

        .gallery-filters {
            display: flex;
            gap: var(--space-sm);
            margin-bottom: var(--space-xl);
            flex-wrap: wrap;
        }

        .gallery-filter {
            padding: var(--space-sm) var(--space-md);
            background: var(--bg-tertiary);
            border: 1px solid var(--border-subtle);
            border-radius: var(--radius-sm);
            color: var(--text-secondary);
            cursor: pointer;
            transition: all var(--duration-fast);
        }

        .gallery-filter.active {
            background: var(--accent-m1k3);
            color: #ffffff;
            border-color: var(--accent-m1k3);
        }

        .gallery-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: var(--space-lg);
        }

        /* Responsive */
        @media (max-width: 768px) {
            .screenshot-grid {
                grid-template-columns: 1fr;
            }
            
            .gallery-grid {
                grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            }
            
            .viewport-tabs {
                overflow-x: auto;
                flex-wrap: nowrap;
            }
        }
        `;
    }

    /**
     * Generate header section
     */
    generateHeader(data) {
        const { metadata, summary } = data;
        
        return `
        <header class="header fade-in">
            <div class="header-content">
                <div class="logo">
                    <span class="logo-text">M1K3</span>
                    <span class="logo-badge">Test Report</span>
                </div>
                <p class="header-subtitle">Comprehensive Test Suite Analysis</p>
                <div class="header-meta">
                    <span>📅 ${new Date(metadata.timestamp).toLocaleString()}</span>
                    <span>🖥️ ${metadata.environment.platform} ${metadata.environment.arch}</span>
                    <span>⏱️ ${data.analytics.total_duration_formatted}</span>
                    <span>🧪 ${summary.total} tests executed</span>
                </div>
            </div>
        </header>`;
    }

    /**
     * Generate executive summary section
     */
    generateExecutiveSummary(data) {
        const { summary, analytics } = data;
        
        return `
        <section class="executive-summary">
            <div class="section-header">
                <h2 class="section-title">Executive Summary</h2>
                <p class="section-subtitle">Key metrics and overall test health</p>
            </div>
            
            <div class="summary-grid">
                <div class="metric-card success slide-in">
                    <span class="metric-number success">${summary.passed}</span>
                    <div class="metric-label">Tests Passed</div>
                    <div class="metric-description">${analytics.success_rate}% success rate</div>
                </div>
                
                <div class="metric-card ${summary.failed > 0 ? 'error' : 'success'} slide-in">
                    <span class="metric-number ${summary.failed > 0 ? 'error' : 'success'}">${summary.failed}</span>
                    <div class="metric-label">Tests Failed</div>
                    <div class="metric-description">${analytics.failure_rate}% failure rate</div>
                </div>
                
                <div class="metric-card info slide-in">
                    <span class="metric-number info">${summary.total}</span>
                    <div class="metric-label">Total Tests</div>
                    <div class="metric-description">Across all categories</div>
                </div>
                
                <div class="metric-card info slide-in">
                    <span class="metric-number info">${analytics.avg_duration}ms</span>
                    <div class="metric-label">Avg Duration</div>
                    <div class="metric-description">Per test execution</div>
                </div>
            </div>
        </section>`;
    }

    /**
     * Generate category breakdown section
     */
    generateCategoryBreakdown(data) {
        const { categories } = data;
        
        const categoryCards = Object.entries(categories).map(([name, category]) => {
            const successRate = category.total > 0 ? (category.passed / category.total * 100).toFixed(1) : 0;
            
            return `
            <div class="category-card slide-in">
                <div class="category-header">
                    <h3 class="category-name">${name}</h3>
                    <span class="category-badge">${category.total} tests</span>
                </div>
                
                <div class="category-stats">
                    <div class="stat-item">
                        <div class="stat-value" style="color: var(--success);">${category.passed}</div>
                        <div class="stat-label">Passed</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value" style="color: var(--error);">${category.failed}</div>
                        <div class="stat-label">Failed</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value" style="color: var(--info);">${this.formatDuration(category.duration)}</div>
                        <div class="stat-label">Duration</div>
                    </div>
                </div>
                
                <div class="progress-bar">
                    <div class="progress-fill" style="width: ${successRate}%;"></div>
                </div>
            </div>`;
        }).join('');
        
        return `
        <section class="section">
            <div class="section-header">
                <h2 class="section-title">Category Breakdown</h2>
                <p class="section-subtitle">Test results organized by category</p>
            </div>
            
            <div class="category-grid">
                ${categoryCards}
            </div>
        </section>`;
    }

    /**
     * Generate performance section
     */
    generatePerformanceSection(data) {
        const { performance } = data;
        
        const slowestTests = performance.slowest_tests?.slice(0, 5).map(test => `
            <tr>
                <td>${test.name}</td>
                <td><span class="status-badge status-${test.status}">${test.status}</span></td>
                <td>${this.formatDuration(test.duration)}</td>
                <td>${test.category}</td>
            </tr>
        `).join('') || '<tr><td colspan="4">No performance data available</td></tr>';
        
        return `
        <section class="section">
            <div class="section-header">
                <h2 class="section-title">Performance Analysis</h2>
                <p class="section-subtitle">Execution time analysis and optimization opportunities</p>
            </div>
            
            <div class="test-results">
                <h3 style="margin-bottom: var(--space-lg); color: var(--text-secondary);">Slowest Tests</h3>
                <table class="test-table">
                    <thead>
                        <tr>
                            <th>Test Name</th>
                            <th>Status</th>
                            <th>Duration</th>
                            <th>Category</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${slowestTests}
                    </tbody>
                </table>
            </div>
        </section>`;
    }

    /**
     * Generate test results section
     */
    generateTestResults(data) {
        const { tests } = data;
        
        const testRows = tests.map(test => `
            <tr class="test-row" data-category="${test.category}" data-status="${test.status}">
                <td>${test.name}</td>
                <td><span class="status-badge status-${test.status}">${test.status}</span></td>
                <td>${test.category}</td>
                <td>${test.framework || 'unknown'}</td>
                <td>${this.formatDuration(test.duration || 0)}</td>
                <td>${test.error ? `<span style="color: var(--error); font-size: 0.875rem;">${test.error.substring(0, 50)}...</span>` : '-'}</td>
            </tr>
        `).join('');
        
        const categories = [...new Set(tests.map(t => t.category))];
        const filterButtons = categories.map(cat => 
            `<button class="filter-btn" data-filter="${cat}">${cat}</button>`
        ).join('');
        
        return `
        <section class="section">
            <div class="section-header">
                <h2 class="section-title">Detailed Test Results</h2>
                <p class="section-subtitle">Complete test execution details</p>
            </div>
            
            <div class="test-results">
                <div class="results-controls">
                    <button class="filter-btn active" data-filter="all">All Tests</button>
                    ${filterButtons}
                    <button class="filter-btn" data-filter="passed">Passed Only</button>
                    <button class="filter-btn" data-filter="failed">Failed Only</button>
                </div>
                
                <table class="test-table">
                    <thead>
                        <tr>
                            <th>Test Name</th>
                            <th>Status</th>
                            <th>Category</th>
                            <th>Framework</th>
                            <th>Duration</th>
                            <th>Error</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${testRows}
                    </tbody>
                </table>
            </div>
        </section>`;
    }

    /**
     * Generate security section
     */
    generateSecuritySection(data) {
        // Placeholder for security audit results
        return `
        <section class="section">
            <div class="section-header">
                <h2 class="section-title">Security Analysis</h2>
                <p class="section-subtitle">Security audit results and recommendations</p>
            </div>
            
            <div class="test-results">
                <div class="metric-card info">
                    <span class="metric-label">Security Audit</span>
                    <p style="margin-top: var(--space-sm); color: var(--text-secondary);">
                        Security audit feature available. Run <code style="background: var(--bg-elevated); padding: 2px 4px; border-radius: 2px;">npm run audit:security</code> 
                        to perform comprehensive security analysis.
                    </p>
                </div>
            </div>
        </section>`;
    }

    /**
     * Generate footer
     */
    generateFooter(data) {
        const { metadata } = data;
        
        return `
        <footer style="background: var(--bg-secondary); padding: var(--space-xl); text-align: center; border-top: 1px solid var(--border-subtle);">
            <div style="color: var(--text-muted); font-size: 0.875rem;">
                <p>Generated by M1K3 Unified Test Suite</p>
                <p style="margin-top: var(--space-xs);">
                    Node.js ${metadata.environment.node_version} • 
                    ${metadata.environment.platform} ${metadata.environment.arch} • 
                    ${new Date().toLocaleString()}
                </p>
            </div>
        </footer>`;
    }

    /**
     * Generate floating particles
     */
    generateFloatingParticles() {
        const particles = Array.from({ length: 10 }, (_, i) => `
            <div class="particle" style="
                left: ${Math.random() * 100}%;
                animation-delay: ${Math.random() * 10}s;
                animation-duration: ${15 + Math.random() * 10}s;
            "></div>
        `).join('');
        
        return `<div class="particles">${particles}</div>`;
    }

    /**
     * Generate interactive JavaScript
     */
    generateScripts() {
        return `
        // Filter functionality
        document.addEventListener('DOMContentLoaded', function() {
            const filterButtons = document.querySelectorAll('.filter-btn');
            const testRows = document.querySelectorAll('.test-row');
            
            filterButtons.forEach(button => {
                button.addEventListener('click', function() {
                    // Update active button
                    filterButtons.forEach(btn => btn.classList.remove('active'));
                    this.classList.add('active');
                    
                    const filter = this.dataset.filter;
                    
                    testRows.forEach(row => {
                        const category = row.dataset.category;
                        const status = row.dataset.status;
                        
                        let show = false;
                        
                        if (filter === 'all') {
                            show = true;
                        } else if (filter === 'passed' || filter === 'failed') {
                            show = status === filter;
                        } else {
                            show = category === filter;
                        }
                        
                        row.style.display = show ? '' : 'none';
                    });
                });
            });
            
            // Animate progress bars
            setTimeout(() => {
                document.querySelectorAll('.progress-fill').forEach(fill => {
                    const width = fill.style.width;
                    fill.style.width = '0%';
                    setTimeout(() => {
                        fill.style.width = width;
                    }, 100);
                });
            }, 500);
            
            // Smooth scroll for links
            document.querySelectorAll('a[href^="#"]').forEach(link => {
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    const target = document.querySelector(this.getAttribute('href'));
                    if (target) {
                        target.scrollIntoView({ behavior: 'smooth' });
                    }
                });
            });

            // Viewport tab functionality
            document.querySelectorAll('.viewport-tab').forEach(tab => {
                tab.addEventListener('click', function() {
                    const parent = this.closest('.journey-step');
                    const viewport = this.dataset.viewport;
                    
                    // Update active tab
                    parent.querySelectorAll('.viewport-tab').forEach(t => t.classList.remove('active'));
                    this.classList.add('active');
                    
                    // Show/hide screenshot grids
                    parent.querySelectorAll('.screenshot-grid').forEach(grid => {
                        grid.style.display = grid.dataset.viewport === viewport ? 'grid' : 'none';
                    });
                });
            });

            // Gallery filter functionality
            document.querySelectorAll('.gallery-filter').forEach(filter => {
                filter.addEventListener('click', function() {
                    // Update active filter
                    document.querySelectorAll('.gallery-filter').forEach(f => f.classList.remove('active'));
                    this.classList.add('active');
                    
                    const filterValue = this.dataset.filter;
                    const items = document.querySelectorAll('.screenshot-item');
                    
                    items.forEach(item => {
                        const category = item.dataset.category;
                        const viewport = item.dataset.viewport;
                        
                        let show = false;
                        if (filterValue === 'all') {
                            show = true;
                        } else if (['app', 'pwa', 'avatar', 'chat', 'dashboard', 'other'].includes(filterValue)) {
                            show = category === filterValue;
                        } else if (['desktop', 'tablet', 'mobile'].includes(filterValue)) {
                            show = viewport === filterValue;
                        }
                        
                        item.style.display = show ? 'block' : 'none';
                    });
                });
            });
        });

        // Screenshot modal functionality
        function openScreenshotModal(imageSrc, altText) {
            const modal = document.querySelector('.screenshot-modal');
            const img = modal.querySelector('img');
            
            img.src = imageSrc;
            img.alt = altText;
            modal.classList.add('active');
        }

        // Close modal on click
        document.addEventListener('DOMContentLoaded', function() {
            const modal = document.querySelector('.screenshot-modal');
            if (modal) {
                modal.addEventListener('click', function() {
                    this.classList.remove('active');
                });
            }
        });
        `;
    }

    /**
     * Utility methods
     */
    formatDuration(ms) {
        if (ms < 1000) return `${Math.round(ms)}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${(ms / 60000).toFixed(1)}m`;
    }

    generateCategorySummary(categories) {
        return Object.entries(categories).map(([name, data]) => ({
            name,
            total: data.total,
            passed: data.passed,
            failed: data.failed,
            success_rate: data.total > 0 ? ((data.passed / data.total) * 100).toFixed(1) : 0
        }));
    }

    generateTrendIndicators(tests) {
        const categories = tests.reduce((acc, test) => {
            if (!acc[test.category]) acc[test.category] = [];
            acc[test.category].push(test);
            return acc;
        }, {});

        return Object.entries(categories).map(([category, categoryTests]) => ({
            category,
            trend: categoryTests.filter(t => t.status === 'passed').length > categoryTests.length / 2 ? 'up' : 'down'
        }));
    }

    generatePerformanceMetrics(performance) {
        return {
            memory_mb: Math.round(performance.memory_usage?.heapUsed / 1024 / 1024) || 0,
            avg_duration: Math.round(performance.avg_test_duration || 0),
            slowest_count: performance.slowest_tests?.length || 0
        };
    }

    generateCategoryChart(categories) {
        // Placeholder for chart data - could integrate Chart.js or similar
        return Object.entries(categories).map(([name, data]) => ({
            category: name,
            passed: data.passed,
            failed: data.failed,
            total: data.total
        }));
    }

    generateTimelineChart(tests) {
        // Placeholder for timeline visualization
        return tests.map(test => ({
            name: test.name,
            start: 0, // Would need actual timing data
            duration: test.duration || 0,
            status: test.status
        }));
    }

    generatePerformanceChart(performance) {
        return {
            avg_duration: performance.avg_test_duration || 0,
            memory_usage: performance.memory_usage?.heapUsed || 0,
            slowest_tests: performance.slowest_tests?.slice(0, 10) || []
        };
    }

    /**
     * Generate user journeys section
     */
    generateUserJourneysSection(data) {
        const { userJourneys } = data;
        
        if (!userJourneys || userJourneys.length === 0) {
            return '';
        }

        return `
        <section class="user-journeys">
            <div class="container">
                <h2 class="section-title">📱 User Journeys</h2>
                <p class="section-subtitle">Visual validation of user workflows across devices</p>
                
                ${userJourneys.map(journey => this.generateJourneyCard(journey)).join('')}
            </div>
        </section>`;
    }

    /**
     * Generate individual journey card
     */
    generateJourneyCard(journey) {
        return `
        <div class="journey-card">
            <div class="journey-header">
                <h3 class="journey-title">${journey.title}</h3>
                <p class="journey-description">${journey.description}</p>
            </div>
            <div class="journey-steps">
                ${journey.steps.map(step => this.generateJourneyStep(step)).join('')}
            </div>
        </div>`;
    }

    /**
     * Generate journey step with screenshots
     */
    generateJourneyStep(step) {
        const hasScreenshots = step.screenshots && 
            (step.screenshots.desktop.length > 0 || 
             step.screenshots.tablet.length > 0 || 
             step.screenshots.mobile.length > 0);

        if (!hasScreenshots) {
            return `
            <div class="journey-step">
                <div class="step-header">
                    <h4 class="step-title">${step.title}</h4>
                    <span class="screenshot-count">No screenshots</span>
                </div>
                <p class="step-description">${step.description}</p>
            </div>`;
        }

        return `
        <div class="journey-step">
            <div class="step-header">
                <h4 class="step-title">${step.title}</h4>
                <span class="screenshot-count">${this.countScreenshots(step.screenshots)} screenshots</span>
            </div>
            <p class="step-description">${step.description}</p>
            
            <div class="viewport-tabs">
                <button class="viewport-tab active" data-viewport="desktop">🖥️ Desktop</button>
                <button class="viewport-tab" data-viewport="tablet">📱 Tablet</button>
                <button class="viewport-tab" data-viewport="mobile">📱 Mobile</button>
            </div>
            
            <div class="screenshot-grid" data-viewport="desktop">
                ${step.screenshots.desktop.map(screenshot => this.generateScreenshotItem(screenshot)).join('')}
            </div>
            <div class="screenshot-grid" data-viewport="tablet" style="display: none;">
                ${step.screenshots.tablet.map(screenshot => this.generateScreenshotItem(screenshot)).join('')}
            </div>
            <div class="screenshot-grid" data-viewport="mobile" style="display: none;">
                ${step.screenshots.mobile.map(screenshot => this.generateScreenshotItem(screenshot)).join('')}
            </div>
        </div>`;
    }

    /**
     * Generate screenshot gallery section
     */
    generateScreenshotGallery(data) {
        const { screenshots } = data;
        
        if (!screenshots || (screenshots.baseline.length === 0 && screenshots.current.length === 0)) {
            return '';
        }

        const allScreenshots = [
            ...screenshots.baseline.map(s => ({ ...s, type: 'baseline' })),
            ...screenshots.current.map(s => ({ ...s, type: 'current' })),
            ...screenshots.diff.map(s => ({ ...s, type: 'diff' }))
        ];

        const categories = [...new Set(allScreenshots.map(s => s.category))];
        const viewports = [...new Set(allScreenshots.map(s => s.viewport))];

        return `
        <section class="screenshot-gallery">
            <div class="container">
                <h2 class="section-title">📸 Screenshot Gallery</h2>
                <p class="section-subtitle">Complete visual test coverage across all routes and viewports</p>
                
                <div class="gallery-filters">
                    <button class="gallery-filter active" data-filter="all">All (${allScreenshots.length})</button>
                    ${categories.map(cat => `
                        <button class="gallery-filter" data-filter="${cat}">
                            ${cat.charAt(0).toUpperCase() + cat.slice(1)} (${allScreenshots.filter(s => s.category === cat).length})
                        </button>
                    `).join('')}
                    ${viewports.map(vp => `
                        <button class="gallery-filter" data-filter="${vp}">
                            ${vp === 'mobile' ? '📱' : vp === 'tablet' ? '📱' : '🖥️'} ${vp.charAt(0).toUpperCase() + vp.slice(1)} (${allScreenshots.filter(s => s.viewport === vp).length})
                        </button>
                    `).join('')}
                </div>
                
                <div class="gallery-grid">
                    ${allScreenshots.map(screenshot => this.generateScreenshotItem(screenshot)).join('')}
                </div>
            </div>
        </section>
        
        <div class="screenshot-modal">
            <img src="" alt="">
        </div>`;
    }

    /**
     * Generate individual screenshot item
     */
    generateScreenshotItem(screenshot) {
        return `
        <div class="screenshot-item" data-category="${screenshot.category}" data-viewport="${screenshot.viewport}">
            <img class="screenshot-image" 
                 src="${screenshot.path}" 
                 alt="${screenshot.name}"
                 onclick="openScreenshotModal('${screenshot.path}', '${screenshot.name}')">
            <div class="screenshot-meta">
                <div class="screenshot-name">${screenshot.name}</div>
                <div class="screenshot-type ${screenshot.type}">${screenshot.type}</div>
            </div>
        </div>`;
    }

    /**
     * Count total screenshots in step
     */
    countScreenshots(screenshots) {
        return screenshots.desktop.length + screenshots.tablet.length + screenshots.mobile.length;
    }
}

module.exports = ReportGenerator;

// CLI usage
if (require.main === module) {
    const generator = new ReportGenerator();
    
    // Example usage with mock data
    const mockResults = {
        metadata: {
            timestamp: new Date().toISOString(),
            environment: {
                node_version: process.version,
                platform: process.platform,
                arch: process.arch
            }
        },
        summary: {
            total: 10,
            passed: 8,
            failed: 2,
            skipped: 0,
            duration: 15000
        },
        categories: {
            unit: { total: 5, passed: 5, failed: 0, duration: 5000 },
            integration: { total: 3, passed: 2, failed: 1, duration: 8000 },
            visual: { total: 2, passed: 1, failed: 1, duration: 2000 }
        },
        tests: [
            { name: 'test1', status: 'passed', category: 'unit', framework: 'jest', duration: 100 },
            { name: 'test2', status: 'failed', category: 'integration', framework: 'playwright', duration: 5000, error: 'Timeout error' }
        ],
        performance: {
            avg_test_duration: 1500,
            slowest_tests: [
                { name: 'slow-test', status: 'passed', duration: 5000, category: 'integration' }
            ],
            memory_usage: { heapUsed: 50000000 }
        }
    };
    
    generator.generateReport(mockResults).then(path => {
        console.log(`✨ Sample report generated: ${path}`);
    }).catch(console.error);
}