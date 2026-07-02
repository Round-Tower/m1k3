/**
 * M1K3 Test Discovery System
 * Automatically discovers and categorizes all test files in the project
 */

const fs = require('fs').promises;
const path = require('path');
const { glob } = require('glob');

class TestDiscovery {
    constructor(options = {}) {
        this.rootDir = options.rootDir || path.resolve('../..');
        this.config = options.config || {};
        this.testRegistry = new Map();
        this.categories = {
            unit: [],
            integration: [],
            visual: [],
            performance: [],
            security: [],
            e2e: [],
            api: []
        };
    }

    /**
     * Discover all test files in the project
     */
    async discoverTests() {
        console.log('🔍 Discovering M1K3 tests...');
        
        const patterns = [
            // JavaScript/Node tests
            '**/test*.js',
            '**/*.test.js',
            '**/*.spec.js',
            '**/tests/**/*.js',
            
            // Python tests
            '**/test_*.py',
            '**/*_test.py',
            '**/tests/**/*.py',
            
            // HTML test pages
            '**/test*.html',
            '**/*test*.html',
            
            // Configuration files
            '**/playwright.config.js',
            '**/jest.config.js',
            '**/pytest.ini'
        ];

        const allFiles = [];
        
        for (const pattern of patterns) {
            try {
                const files = await glob(pattern, {
                    cwd: this.rootDir,
                    ignore: ['**/node_modules/**', '**/venv/**', '**/.git/**'],
                    absolute: true
                });
                allFiles.push(...files);
            } catch (error) {
                console.warn(`Warning: Pattern ${pattern} failed:`, error.message);
            }
        }

        // Remove duplicates and categorize
        const uniqueFiles = [...new Set(allFiles)];
        
        for (const filePath of uniqueFiles) {
            await this.categorizeTest(filePath);
        }

        return this.generateDiscoveryReport();
    }

    /**
     * Categorize a test file based on its content and location
     */
    async categorizeTest(filePath) {
        try {
            const stat = await fs.stat(filePath);
            if (!stat.isFile()) return;

            const content = await fs.readFile(filePath, 'utf-8');
            const relativePath = path.relative(this.rootDir, filePath);
            const extension = path.extname(filePath);
            const basename = path.basename(filePath);

            const testInfo = {
                path: filePath,
                relativePath: relativePath,
                name: basename,
                extension: extension,
                size: stat.size,
                modified: stat.mtime,
                category: 'unknown',
                subcategory: '',
                framework: this.detectFramework(content, extension),
                dependencies: this.extractDependencies(content, extension),
                testCount: this.countTests(content, extension),
                tags: this.extractTags(content, relativePath),
                estimated_duration: this.estimateDuration(content, extension)
            };

            // Categorize based on patterns
            testInfo.category = this.determineCategory(testInfo, content);
            
            this.testRegistry.set(filePath, testInfo);
            this.categories[testInfo.category].push(testInfo);

        } catch (error) {
            console.warn(`Failed to process ${filePath}:`, error.message);
        }
    }

    /**
     * Determine test category based on file content and patterns
     */
    determineCategory(testInfo, content) {
        const { relativePath, name } = testInfo;
        
        // Visual/Screenshot tests
        if (relativePath.includes('screenshot') || 
            content.includes('screenshot') || 
            content.includes('visual') ||
            content.includes('playwright')) {
            return 'visual';
        }
        
        // Security tests
        if (relativePath.includes('security') || 
            name.includes('security') ||
            content.includes('audit') ||
            content.includes('vulnerability')) {
            return 'security';
        }
        
        // Performance tests
        if (relativePath.includes('performance') || 
            name.includes('performance') ||
            content.includes('benchmark') ||
            content.includes('timing')) {
            return 'performance';
        }
        
        // Integration tests
        if (relativePath.includes('integration') || 
            name.includes('integration') ||
            content.includes('integration') ||
            content.includes('full_system')) {
            return 'integration';
        }
        
        // E2E tests
        if (relativePath.includes('e2e') || 
            name.includes('e2e') ||
            content.includes('end-to-end') ||
            content.includes('journey')) {
            return 'e2e';
        }
        
        // API tests
        if (relativePath.includes('api') || 
            content.includes('request') ||
            content.includes('endpoint') ||
            content.includes('websocket')) {
            return 'api';
        }
        
        // Default to unit tests
        return 'unit';
    }

    /**
     * Detect testing framework used
     */
    detectFramework(content, extension) {
        if (extension === '.py') {
            if (content.includes('import pytest')) return 'pytest';
            if (content.includes('import unittest')) return 'unittest';
            if (content.includes('def test_')) return 'python-custom';
        }
        
        if (extension === '.js') {
            if (content.includes('playwright')) return 'playwright';
            if (content.includes('describe(') || content.includes('it(')) return 'jest/mocha';
            if (content.includes('test(')) return 'jest';
            if (content.includes('QUnit')) return 'qunit';
        }
        
        if (extension === '.html') {
            if (content.includes('qunit')) return 'qunit';
            if (content.includes('mocha')) return 'mocha';
            return 'html-manual';
        }
        
        return 'unknown';
    }

    /**
     * Extract dependencies from test files
     */
    extractDependencies(content, extension) {
        const deps = [];
        
        if (extension === '.py') {
            const imports = content.match(/^(?:from|import)\s+(\w+)/gm) || [];
            deps.push(...imports.map(imp => imp.split(/\s+/)[1]));
        }
        
        if (extension === '.js') {
            const requires = content.match(/require\(['"]([^'"]+)['"]\)/g) || [];
            const imports = content.match(/import.*from\s+['"]([^'"]+)['"]/g) || [];
            
            requires.forEach(req => {
                const match = req.match(/require\(['"]([^'"]+)['"]\)/);
                if (match) deps.push(match[1]);
            });
            
            imports.forEach(imp => {
                const match = imp.match(/from\s+['"]([^'"]+)['"]/);
                if (match) deps.push(match[1]);
            });
        }
        
        return [...new Set(deps)];
    }

    /**
     * Count number of tests in a file
     */
    countTests(content, extension) {
        let count = 0;
        
        if (extension === '.py') {
            count += (content.match(/def test_\w+/g) || []).length;
        }
        
        if (extension === '.js') {
            count += (content.match(/test\s*\(/g) || []).length;
            count += (content.match(/it\s*\(/g) || []).length;
        }
        
        return count;
    }

    /**
     * Extract tags from test content
     */
    extractTags(content, relativePath) {
        const tags = [];
        
        // Path-based tags
        if (relativePath.includes('screenshot')) tags.push('visual');
        if (relativePath.includes('mobile')) tags.push('mobile');
        if (relativePath.includes('desktop')) tags.push('desktop');
        if (relativePath.includes('pwa')) tags.push('pwa');
        if (relativePath.includes('avatar')) tags.push('avatar');
        if (relativePath.includes('voice')) tags.push('voice');
        if (relativePath.includes('ai')) tags.push('ai');
        
        // Content-based tags
        if (content.includes('@slow')) tags.push('slow');
        if (content.includes('@fast')) tags.push('fast');
        if (content.includes('@critical')) tags.push('critical');
        if (content.includes('@flaky')) tags.push('flaky');
        if (content.includes('localhost')) tags.push('local');
        
        return tags;
    }

    /**
     * Estimate test duration based on content
     */
    estimateDuration(content, extension) {
        // Base duration by type
        let duration = 1000; // 1 second default
        
        if (content.includes('screenshot') || content.includes('visual')) {
            duration = 5000; // 5 seconds for visual tests
        }
        
        if (content.includes('integration') || content.includes('e2e')) {
            duration = 10000; // 10 seconds for integration
        }
        
        if (content.includes('performance') || content.includes('benchmark')) {
            duration = 15000; // 15 seconds for performance
        }
        
        // Adjust based on content length
        const complexity = Math.min(content.length / 1000, 5);
        duration *= (1 + complexity * 0.2);
        
        return Math.round(duration);
    }

    /**
     * Generate discovery report
     */
    generateDiscoveryReport() {
        const totalTests = Array.from(this.testRegistry.values());
        const totalTestCount = totalTests.reduce((sum, test) => sum + test.testCount, 0);
        const totalDuration = totalTests.reduce((sum, test) => sum + test.estimated_duration, 0);
        
        const report = {
            timestamp: new Date().toISOString(),
            summary: {
                total_files: totalTests.length,
                total_tests: totalTestCount,
                estimated_duration_ms: totalDuration,
                estimated_duration_readable: this.formatDuration(totalDuration)
            },
            categories: {},
            frameworks: {},
            tags: {},
            files: totalTests
        };

        // Category breakdown
        Object.keys(this.categories).forEach(category => {
            const tests = this.categories[category];
            report.categories[category] = {
                count: tests.length,
                test_count: tests.reduce((sum, test) => sum + test.testCount, 0),
                duration: tests.reduce((sum, test) => sum + test.estimated_duration, 0)
            };
        });

        // Framework breakdown
        totalTests.forEach(test => {
            if (!report.frameworks[test.framework]) {
                report.frameworks[test.framework] = 0;
            }
            report.frameworks[test.framework]++;
        });

        // Tag analysis
        totalTests.forEach(test => {
            test.tags.forEach(tag => {
                if (!report.tags[tag]) {
                    report.tags[tag] = 0;
                }
                report.tags[tag]++;
            });
        });

        return report;
    }

    /**
     * Format duration in human readable form
     */
    formatDuration(ms) {
        if (ms < 1000) return `${ms}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${(ms / 60000).toFixed(1)}m`;
    }

    /**
     * Get tests by category
     */
    getTestsByCategory(category) {
        return this.categories[category] || [];
    }

    /**
     * Get tests by tag
     */
    getTestsByTag(tag) {
        return Array.from(this.testRegistry.values())
            .filter(test => test.tags.includes(tag));
    }

    /**
     * Get critical path tests (fastest, most important)
     */
    getCriticalPathTests() {
        return Array.from(this.testRegistry.values())
            .filter(test => 
                test.tags.includes('critical') || 
                test.category === 'unit' ||
                test.estimated_duration < 5000
            )
            .sort((a, b) => a.estimated_duration - b.estimated_duration);
    }
}

module.exports = TestDiscovery;

// CLI usage
if (require.main === module) {
    const discovery = new TestDiscovery({
        rootDir: path.resolve('../..')
    });

    discovery.discoverTests().then(report => {
        console.log('🎯 M1K3 Test Discovery Report');
        console.log('============================');
        console.log(`📁 Total files: ${report.summary.total_files}`);
        console.log(`🧪 Total tests: ${report.summary.total_tests}`);
        console.log(`⏱️  Estimated duration: ${report.summary.estimated_duration_readable}`);
        console.log('\n📊 Categories:');
        
        Object.entries(report.categories).forEach(([category, data]) => {
            if (data.count > 0) {
                console.log(`   ${category}: ${data.count} files, ${data.test_count} tests`);
            }
        });
        
        console.log('\n🔧 Frameworks:');
        Object.entries(report.frameworks).forEach(([framework, count]) => {
            console.log(`   ${framework}: ${count} files`);
        });

        // Save report
        const outputPath = path.join(__dirname, 'output/data/test-discovery.json');
        require('fs').writeFileSync(outputPath, JSON.stringify(report, null, 2));
        console.log(`\n💾 Report saved to: ${outputPath}`);
        
    }).catch(error => {
        console.error('❌ Discovery failed:', error);
        process.exit(1);
    });
}