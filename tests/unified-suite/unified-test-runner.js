#!/usr/bin/env node
/**
 * M1K3 Unified Test Runner
 * Orchestrates all test types with parallel execution and comprehensive reporting
 */

const fs = require('fs').promises;
const path = require('path');
const { spawn } = require('child_process');
const { performance } = require('perf_hooks');
const TestDiscovery = require('./test-discovery');

class UnifiedTestRunner {
    constructor(options = {}) {
        this.startTime = performance.now();
        this.rootDir = options.rootDir || path.resolve('../..');
        this.outputDir = path.join(__dirname, 'output');
        this.config = options.config || this.loadConfig();
        
        this.results = {
            metadata: {
                timestamp: new Date().toISOString(),
                environment: this.getEnvironmentInfo(),
                config: this.config
            },
            summary: {
                total: 0,
                passed: 0,
                failed: 0,
                skipped: 0,
                duration: 0
            },
            categories: {},
            tests: [],
            performance: {},
            errors: []
        };

        this.discovery = new TestDiscovery({ rootDir: this.rootDir });
        this.runningTests = new Map();
    }

    /**
     * Load configuration
     */
    loadConfig() {
        try {
            const configPath = path.join(__dirname, 'config/test.config.js');
            return require(configPath);
        } catch {
            return {
                parallel: true,
                maxConcurrency: 4,
                timeout: 30000,
                retries: 1,
                categories: ['unit', 'integration', 'visual'],
                generateReport: true,
                exportData: true
            };
        }
    }

    /**
     * Get environment information
     */
    getEnvironmentInfo() {
        return {
            node_version: process.version,
            platform: process.platform,
            arch: process.arch,
            memory: Math.round(process.memoryUsage().heapTotal / 1024 / 1024) + 'MB',
            cwd: process.cwd(),
            user: process.env.USER || process.env.USERNAME,
            ci: !!process.env.CI,
            git_commit: this.getGitCommit()
        };
    }

    /**
     * Get git commit information
     */
    getGitCommit() {
        try {
            const { execSync } = require('child_process');
            return {
                sha: execSync('git rev-parse HEAD', { encoding: 'utf8' }).trim(),
                branch: execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8' }).trim(),
                author: execSync('git log -1 --pretty=format:"%an"', { encoding: 'utf8' }).trim(),
                message: execSync('git log -1 --pretty=format:"%s"', { encoding: 'utf8' }).trim()
            };
        } catch {
            return { sha: 'unknown', branch: 'unknown' };
        }
    }

    /**
     * Run all tests
     */
    async runTests(options = {}) {
        console.log('🚀 M1K3 Unified Test Suite Starting...');
        console.log('=====================================');
        
        try {
            // Discover tests
            console.log('🔍 Discovering tests...');
            const discoveryReport = await this.discovery.discoverTests();
            this.logDiscoveryResults(discoveryReport);

            // Filter tests based on options
            const testsToRun = this.filterTests(discoveryReport, options);
            console.log(`\n🎯 Running ${testsToRun.length} test files...`);

            // Execute tests by category
            await this.executeTestsByCategory(testsToRun, options);

            // Calculate final results
            this.calculateSummary();

            // Generate reports
            if (options.generateReport !== false) {
                await this.generateReports();
            }

            this.logFinalResults();
            
            return this.results;

        } catch (error) {
            console.error('❌ Test execution failed:', error);
            this.results.errors.push({
                type: 'execution_error',
                message: error.message,
                stack: error.stack,
                timestamp: new Date().toISOString()
            });
            throw error;
        }
    }

    /**
     * Filter tests based on options
     */
    filterTests(discoveryReport, options) {
        let tests = discoveryReport.files;

        // Filter by categories
        if (options.categories) {
            const targetCategories = Array.isArray(options.categories) 
                ? options.categories 
                : options.categories.split(',');
            tests = tests.filter(test => targetCategories.includes(test.category));
        }

        // Filter by tags
        if (options.tags) {
            const targetTags = Array.isArray(options.tags) 
                ? options.tags 
                : options.tags.split(',');
            tests = tests.filter(test => 
                targetTags.some(tag => test.tags.includes(tag))
            );
        }

        // Filter by changed files (TDD mode)
        if (options.changedOnly) {
            tests = this.getChangedTests(tests);
        }

        return tests;
    }

    /**
     * Execute tests organized by category
     */
    async executeTestsByCategory(tests, options) {
        const categories = this.groupByCategory(tests);
        
        for (const [category, categoryTests] of Object.entries(categories)) {
            console.log(`\n📂 Running ${category} tests (${categoryTests.length} files)...`);
            
            const categoryResults = await this.executeTestCategory(category, categoryTests, options);
            this.results.categories[category] = categoryResults;
        }
    }

    /**
     * Execute tests for a specific category
     */
    async executeTestCategory(category, tests, options) {
        const categoryResult = {
            name: category,
            total: tests.length,
            passed: 0,
            failed: 0,
            skipped: 0,
            duration: 0,
            tests: []
        };

        const startTime = performance.now();

        if (options.parallel && this.config.parallel) {
            // Run tests in parallel with concurrency limit
            await this.executeTestsParallel(tests, categoryResult, options);
        } else {
            // Run tests sequentially
            await this.executeTestsSequential(tests, categoryResult, options);
        }

        categoryResult.duration = performance.now() - startTime;
        return categoryResult;
    }

    /**
     * Execute tests in parallel
     */
    async executeTestsParallel(tests, categoryResult, options) {
        const concurrency = options.concurrency || this.config.maxConcurrency;
        const chunks = this.chunkArray(tests, concurrency);

        for (const chunk of chunks) {
            const promises = chunk.map(test => this.executeTest(test, options));
            const results = await Promise.allSettled(promises);
            
            results.forEach((result, index) => {
                const test = chunk[index];
                if (result.status === 'fulfilled') {
                    this.processTestResult(result.value, categoryResult);
                } else {
                    this.processTestError(test, result.reason, categoryResult);
                }
            });
        }
    }

    /**
     * Execute tests sequentially
     */
    async executeTestsSequential(tests, categoryResult, options) {
        for (const test of tests) {
            try {
                const result = await this.executeTest(test, options);
                this.processTestResult(result, categoryResult);
            } catch (error) {
                this.processTestError(test, error, categoryResult);
            }
        }
    }

    /**
     * Execute a single test
     */
    async executeTest(test, options) {
        const testResult = {
            name: test.name,
            path: test.relativePath,
            category: test.category,
            framework: test.framework,
            startTime: performance.now(),
            status: 'running'
        };

        console.log(`   🧪 ${test.name}`);

        try {
            const executor = this.getTestExecutor(test);
            const result = await executor.execute(test, options);
            
            testResult.endTime = performance.now();
            testResult.duration = testResult.endTime - testResult.startTime;
            testResult.status = result.success ? 'passed' : 'failed';
            testResult.details = result.details;
            testResult.stdout = result.stdout;
            testResult.stderr = result.stderr;

            return testResult;

        } catch (error) {
            testResult.endTime = performance.now();
            testResult.duration = testResult.endTime - testResult.startTime;
            testResult.status = 'failed';
            testResult.error = error.message;
            testResult.stack = error.stack;
            
            throw error;
        }
    }

    /**
     * Get appropriate test executor for test type
     */
    getTestExecutor(test) {
        switch (test.framework) {
            case 'playwright':
                return new PlaywrightExecutor();
            case 'pytest':
            case 'unittest':
            case 'python-custom':
                return new PythonExecutor();
            case 'jest':
            case 'jest/mocha':
                return new NodeExecutor();
            default:
                return new GenericExecutor();
        }
    }

    /**
     * Process successful test result
     */
    processTestResult(result, categoryResult) {
        if (result.status === 'passed') {
            categoryResult.passed++;
            console.log(`      ✅ ${result.name} (${Math.round(result.duration)}ms)`);
        } else {
            categoryResult.failed++;
            console.log(`      ❌ ${result.name} (${Math.round(result.duration)}ms)`);
            console.log(`         ${result.error || 'Test failed'}`);
        }

        categoryResult.tests.push(result);
        this.results.tests.push(result);
    }

    /**
     * Process test error
     */
    processTestError(test, error, categoryResult) {
        categoryResult.failed++;
        console.log(`      💥 ${test.name} - ${error.message}`);

        const errorResult = {
            name: test.name,
            path: test.relativePath,
            category: test.category,
            status: 'failed',
            error: error.message,
            stack: error.stack,
            duration: 0
        };

        categoryResult.tests.push(errorResult);
        this.results.tests.push(errorResult);
        this.results.errors.push({
            test: test.name,
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }

    /**
     * Calculate final summary statistics
     */
    calculateSummary() {
        this.results.summary.total = this.results.tests.length;
        this.results.summary.passed = this.results.tests.filter(t => t.status === 'passed').length;
        this.results.summary.failed = this.results.tests.filter(t => t.status === 'failed').length;
        this.results.summary.skipped = this.results.tests.filter(t => t.status === 'skipped').length;
        this.results.summary.duration = performance.now() - this.startTime;
        
        // Performance metrics
        this.results.performance = {
            avg_test_duration: this.results.tests.reduce((sum, t) => sum + (t.duration || 0), 0) / this.results.tests.length,
            slowest_tests: this.results.tests
                .filter(t => t.duration)
                .sort((a, b) => b.duration - a.duration)
                .slice(0, 10),
            memory_usage: process.memoryUsage(),
            total_duration_readable: this.formatDuration(this.results.summary.duration)
        };
    }

    /**
     * Generate reports
     */
    async generateReports() {
        console.log('\n📊 Generating reports...');

        // Ensure output directories exist
        await this.ensureOutputDirectories();

        // Generate JSON report
        const jsonPath = path.join(this.outputDir, 'data', `test-results-${Date.now()}.json`);
        await fs.writeFile(jsonPath, JSON.stringify(this.results, null, 2));
        console.log(`   💾 JSON report: ${jsonPath}`);

        // Generate HTML report
        const ReportGenerator = require('./report-generator');
        const reportGenerator = new ReportGenerator();
        const htmlPath = await reportGenerator.generateReport(this.results);
        console.log(`   📄 HTML report: ${htmlPath}`);

        return { json: jsonPath, html: htmlPath };
    }

    /**
     * Utility methods
     */
    logDiscoveryResults(report) {
        console.log(`   📁 Found ${report.summary.total_files} test files`);
        console.log(`   🧪 Total ${report.summary.total_tests} individual tests`);
        console.log(`   ⏱️  Estimated duration: ${report.summary.estimated_duration_readable}`);
    }

    logFinalResults() {
        const { summary } = this.results;
        const success = summary.failed === 0;
        
        console.log('\n🎯 Test Results Summary');
        console.log('======================');
        console.log(`✅ Passed: ${summary.passed}`);
        console.log(`❌ Failed: ${summary.failed}`);
        console.log(`⏭️  Skipped: ${summary.skipped}`);
        console.log(`📊 Total: ${summary.total}`);
        console.log(`⏱️  Duration: ${this.formatDuration(summary.duration)}`);
        
        if (success) {
            console.log('\n🎉 All tests passed!');
        } else {
            console.log(`\n💥 ${summary.failed} test(s) failed`);
            process.exitCode = 1;
        }
    }

    groupByCategory(tests) {
        return tests.reduce((groups, test) => {
            const category = test.category || 'unknown';
            if (!groups[category]) groups[category] = [];
            groups[category].push(test);
            return groups;
        }, {});
    }

    chunkArray(array, size) {
        const chunks = [];
        for (let i = 0; i < array.length; i += size) {
            chunks.push(array.slice(i, i + size));
        }
        return chunks;
    }

    formatDuration(ms) {
        if (ms < 1000) return `${Math.round(ms)}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        return `${(ms / 60000).toFixed(1)}m`;
    }

    async ensureOutputDirectories() {
        const dirs = ['reports', 'data', 'artifacts'].map(d => path.join(this.outputDir, d));
        for (const dir of dirs) {
            try {
                await fs.access(dir);
            } catch {
                await fs.mkdir(dir, { recursive: true });
            }
        }
    }
}

/**
 * Test Executors for different frameworks
 */
class PlaywrightExecutor {
    async execute(test, options) {
        // Execute Playwright/screenshot tests
        const { spawn } = require('child_process');
        
        return new Promise((resolve, reject) => {
            const process = spawn('node', [test.path], {
                cwd: path.dirname(test.path),
                stdio: 'pipe'
            });

            let stdout = '';
            let stderr = '';

            process.stdout.on('data', (data) => stdout += data.toString());
            process.stderr.on('data', (data) => stderr += data.toString());

            process.on('close', (code) => {
                resolve({
                    success: code === 0,
                    stdout,
                    stderr,
                    details: { exit_code: code }
                });
            });

            process.on('error', reject);
        });
    }
}

class PythonExecutor {
    async execute(test, options) {
        const { spawn } = require('child_process');
        
        return new Promise((resolve, reject) => {
            const process = spawn('python3', [test.path], {
                cwd: path.dirname(test.path),
                stdio: 'pipe'
            });

            let stdout = '';
            let stderr = '';

            process.stdout.on('data', (data) => stdout += data.toString());
            process.stderr.on('data', (data) => stderr += data.toString());

            process.on('close', (code) => {
                resolve({
                    success: code === 0,
                    stdout,
                    stderr,
                    details: { exit_code: code }
                });
            });

            process.on('error', reject);
        });
    }
}

class NodeExecutor {
    async execute(test, options) {
        const { spawn } = require('child_process');
        
        return new Promise((resolve, reject) => {
            const process = spawn('node', [test.path], {
                cwd: path.dirname(test.path),
                stdio: 'pipe'
            });

            let stdout = '';
            let stderr = '';

            process.stdout.on('data', (data) => stdout += data.toString());
            process.stderr.on('data', (data) => stderr += data.toString());

            process.on('close', (code) => {
                resolve({
                    success: code === 0,
                    stdout,
                    stderr,
                    details: { exit_code: code }
                });
            });

            process.on('error', reject);
        });
    }
}

class GenericExecutor {
    async execute(test, options) {
        // For HTML and other files, just verify they exist and are readable
        try {
            await fs.access(test.path);
            const content = await fs.readFile(test.path, 'utf-8');
            
            return {
                success: true,
                stdout: `File verified: ${test.path}`,
                stderr: '',
                details: { 
                    size: content.length,
                    type: 'static_verification'
                }
            };
        } catch (error) {
            return {
                success: false,
                stdout: '',
                stderr: error.message,
                details: { error: error.message }
            };
        }
    }
}

module.exports = UnifiedTestRunner;

// CLI usage
if (require.main === module) {
    const { Command } = require('commander');
    const program = new Command();

    program
        .name('m1k3-test')
        .description('M1K3 Unified Test Suite')
        .version('1.0.0')
        .option('-c, --categories <categories>', 'Test categories to run (comma-separated)')
        .option('-t, --tags <tags>', 'Test tags to filter by (comma-separated)')
        .option('-p, --parallel', 'Run tests in parallel')
        .option('-s, --sequential', 'Run tests sequentially')
        .option('--no-report', 'Skip report generation')
        .option('--changed-only', 'Run only tests for changed files')
        .option('--timeout <ms>', 'Test timeout in milliseconds')
        .option('--verbose', 'Verbose output');

    program.parse();
    const options = program.opts();

    const runner = new UnifiedTestRunner();
    runner.runTests(options).catch(error => {
        console.error('💥 Test suite failed:', error.message);
        process.exit(1);
    });
}