/**
 * M1K3 TDD Workflow Helper
 * Provides tools to facilitate Test-Driven Development workflow
 */

const fs = require('fs').promises;
const path = require('path');
const { spawn } = require('child_process');

class TDDHelper {
    constructor(options = {}) {
        this.rootDir = options.rootDir || path.resolve('../..');
        this.testDir = options.testDir || path.resolve('.');
        this.config = options.config || {};
        this.watchMode = false;
        this.testResults = new Map();
        this.failedTests = [];
        this.passedTests = [];
        this.coverage = {};
    }

    /**
     * Initialize TDD workflow
     */
    async initializeTDD() {
        console.log('🚀 Initializing M1K3 TDD Workflow...\n');
        
        const workflow = {
            timestamp: new Date().toISOString(),
            mode: 'tdd',
            status: 'initialized',
            steps: []
        };

        // Create TDD directories if needed
        await this.ensureTDDStructure();
        
        // Analyze current test coverage
        const coverage = await this.analyzeCoverage();
        workflow.coverage = coverage;
        
        // Identify areas needing tests
        const gaps = await this.identifyTestGaps();
        workflow.test_gaps = gaps;
        
        // Generate test templates
        const templates = await this.generateTestTemplates(gaps);
        workflow.generated_templates = templates;
        
        console.log('✅ TDD Workflow initialized successfully');
        console.log(`📊 Coverage: ${coverage.percentage}%`);
        console.log(`🔍 Test gaps identified: ${gaps.length}`);
        console.log(`📝 Templates generated: ${templates.length}\n`);
        
        return workflow;
    }

    /**
     * Run TDD cycle: Red → Green → Refactor
     */
    async runTDDCycle(options = {}) {
        console.log('🔄 Starting TDD Cycle...\n');
        
        const cycle = {
            timestamp: new Date().toISOString(),
            phase: 'red',
            results: {}
        };

        try {
            // Phase 1: RED - Write failing test
            console.log('🔴 Phase 1: RED - Running tests (expect failures)');
            const redResults = await this.runTests({ expectFailures: true });
            cycle.results.red = redResults;
            
            if (redResults.failed_count === 0) {
                console.log('⚠️  No failing tests found. Write a failing test first!');
                return cycle;
            }
            
            console.log(`❌ ${redResults.failed_count} tests failing (expected)\n`);
            
            // Phase 2: GREEN - Write minimal code to pass
            console.log('🟢 Phase 2: GREEN - Implement minimal solution');
            console.log('💡 Implement code to make tests pass, then press Enter to continue...');
            
            if (!options.automated) {
                await this.waitForUserInput();
            }
            
            const greenResults = await this.runTests({ expectSuccess: true });
            cycle.results.green = greenResults;
            cycle.phase = 'green';
            
            if (greenResults.failed_count > 0) {
                console.log(`❌ ${greenResults.failed_count} tests still failing`);
                console.log('🔧 Continue implementing until all tests pass\n');
                return cycle;
            }
            
            console.log('✅ All tests passing!\n');
            
            // Phase 3: REFACTOR - Improve code quality
            console.log('🔧 Phase 3: REFACTOR - Improve code quality');
            const refactorSuggestions = await this.generateRefactorSuggestions();
            cycle.results.refactor = refactorSuggestions;
            cycle.phase = 'refactor';
            
            console.log(`💡 Refactor suggestions: ${refactorSuggestions.length}`);
            
            // Final verification
            const finalResults = await this.runTests({ final: true });
            cycle.results.final = finalResults;
            
            if (finalResults.failed_count === 0) {
                console.log('🎉 TDD Cycle completed successfully!\n');
                cycle.status = 'completed';
            } else {
                console.log('❌ Final tests failed. Refactoring broke something!\n');
                cycle.status = 'refactor_failed';
            }
            
            return cycle;
            
        } catch (error) {
            console.error('❌ TDD Cycle failed:', error.message);
            cycle.status = 'error';
            cycle.error = error.message;
            return cycle;
        }
    }

    /**
     * Watch mode for continuous testing
     */
    async startWatchMode() {
        console.log('👀 Starting TDD Watch Mode...\n');
        this.watchMode = true;
        
        const chokidar = require('chokidar');
        
        // Watch for file changes
        const watcher = chokidar.watch([
            path.join(this.rootDir, '**/*.js'),
            path.join(this.rootDir, '**/*.py'),
            path.join(this.rootDir, '**/*.html'),
            path.join(this.rootDir, '**/*.css')
        ], {
            ignored: [
                '**/node_modules/**',
                '**/venv/**',
                '**/.git/**',
                '**/coverage/**',
                '**/output/**'
            ],
            ignoreInitial: true
        });

        watcher.on('change', async (filePath) => {
            console.log(`📝 File changed: ${path.relative(this.rootDir, filePath)}`);
            await this.runQuickTests();
        });

        watcher.on('add', async (filePath) => {
            console.log(`📄 File added: ${path.relative(this.rootDir, filePath)}`);
            await this.runQuickTests();
        });

        console.log('👁️  Watching for changes... (Ctrl+C to stop)');
        
        // Keep the process alive
        process.on('SIGINT', () => {
            console.log('\n🛑 Stopping watch mode...');
            watcher.close();
            this.watchMode = false;
            process.exit(0);
        });
        
        // Run initial test
        await this.runQuickTests();
    }

    /**
     * Run quick subset of tests
     */
    async runQuickTests() {
        console.log('\n🏃‍♂️ Running quick tests...');
        
        try {
            const TestRunner = require('./unified-test-runner');
            const runner = new TestRunner({
                rootDir: this.rootDir,
                quick: true
            });
            
            const results = await runner.runTests({
                categories: ['unit'],
                tags: ['fast', 'critical'],
                maxDuration: 30000 // 30 seconds max
            });
            
            const summary = results.summary;
            if (summary.failed_count === 0) {
                console.log(`✅ Quick tests passed (${summary.passed_count}/${summary.total_count})`);
            } else {
                console.log(`❌ Quick tests failed (${summary.failed_count}/${summary.total_count})`);
                this.showFailedTests(results.failed_tests);
            }
            
        } catch (error) {
            console.error('❌ Quick test run failed:', error.message);
        }
    }

    /**
     * Generate test templates for missing coverage
     */
    async generateTestTemplates(gaps) {
        const templates = [];
        
        for (const gap of gaps) {
            const template = await this.createTestTemplate(gap);
            if (template) {
                templates.push(template);
            }
        }
        
        return templates;
    }

    /**
     * Create individual test template
     */
    async createTestTemplate(gap) {
        const { file, functions, coverage } = gap;
        const ext = path.extname(file);
        
        try {
            let template;
            if (ext === '.js') {
                template = await this.createJavaScriptTestTemplate(gap);
            } else if (ext === '.py') {
                template = await this.createPythonTestTemplate(gap);
            } else {
                return null;
            }
            
            const testFile = this.getTestFilePath(file);
            await fs.writeFile(testFile, template);
            
            console.log(`📝 Generated test template: ${path.relative(this.rootDir, testFile)}`);
            
            return {
                source_file: file,
                test_file: testFile,
                functions_to_test: functions.length,
                template_type: ext === '.js' ? 'javascript' : 'python'
            };
            
        } catch (error) {
            console.warn(`Failed to generate template for ${file}:`, error.message);
            return null;
        }
    }

    /**
     * Create JavaScript test template
     */
    async createJavaScriptTestTemplate(gap) {
        const { file, functions } = gap;
        const className = path.basename(file, '.js');
        const relativePath = path.relative(this.testDir, file);
        
        let template = `/**
 * Test suite for ${className}
 * Generated by M1K3 TDD Helper
 */

const ${className} = require('${relativePath.replace(/\\/g, '/')}');

describe('${className}', () => {
    let instance;
    
    beforeEach(() => {
        // Setup before each test
        instance = new ${className}();
    });
    
    afterEach(() => {
        // Cleanup after each test
        instance = null;
    });

`;

        functions.forEach(func => {
            template += `    describe('${func.name}', () => {
        test('should ${func.name.replace(/([A-Z])/g, ' $1').toLowerCase()}', () => {
            // TODO: Write test for ${func.name}
            // Arrange
            const input = null;
            const expected = null;
            
            // Act
            const result = instance.${func.name}(input);
            
            // Assert
            expect(result).toBe(expected);
        });
        
        test('should handle edge cases for ${func.name}', () => {
            // TODO: Test edge cases
            expect(() => instance.${func.name}(null)).not.toThrow();
        });
    });

`;
        });

        template += `});
`;

        return template;
    }

    /**
     * Create Python test template
     */
    async createPythonTestTemplate(gap) {
        const { file, functions } = gap;
        const className = path.basename(file, '.py');
        const relativePath = path.relative(this.testDir, file).replace(/\\/g, '/');
        
        let template = `"""
Test suite for ${className}
Generated by M1K3 TDD Helper
"""

import unittest
from unittest.mock import Mock, patch
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from ${relativePath.replace('/', '.').replace('.py', '')} import *


class Test${className.charAt(0).toUpperCase() + className.slice(1)}(unittest.TestCase):
    """Test cases for ${className}"""
    
    def setUp(self):
        """Set up test fixtures before each test method."""
        self.instance = ${className}()
    
    def tearDown(self):
        """Clean up after each test method."""
        self.instance = None

`;

        functions.forEach(func => {
            const testName = func.name.replace(/([A-Z])/g, '_$1').toLowerCase();
            template += `    def test_${testName}(self):
        """Test ${func.name} method"""
        # TODO: Write test for ${func.name}
        # Arrange
        input_data = None
        expected = None
        
        # Act
        result = self.instance.${func.name}(input_data)
        
        # Assert
        self.assertEqual(result, expected)
    
    def test_${testName}_edge_cases(self):
        """Test edge cases for ${func.name}"""
        # TODO: Test edge cases
        with self.assertRaises(TypeError):
            self.instance.${func.name}(None)

`;
        });

        template += `
if __name__ == '__main__':
    unittest.main()
`;

        return template;
    }

    /**
     * Analyze current test coverage
     */
    async analyzeCoverage() {
        console.log('📊 Analyzing test coverage...');
        
        // Find all source files
        const sourceFiles = await this.findSourceFiles();
        
        // Find all test files  
        const testFiles = await this.findTestFiles();
        
        // Calculate coverage
        const coverage = {
            total_files: sourceFiles.length,
            tested_files: 0,
            untested_files: [],
            percentage: 0,
            by_category: {
                javascript: { total: 0, tested: 0 },
                python: { total: 0, tested: 0 },
                html: { total: 0, tested: 0 }
            }
        };
        
        for (const sourceFile of sourceFiles) {
            const hasTest = this.hasTestFile(sourceFile, testFiles);
            const ext = path.extname(sourceFile);
            
            if (ext === '.js') {
                coverage.by_category.javascript.total++;
                if (hasTest) coverage.by_category.javascript.tested++;
            } else if (ext === '.py') {
                coverage.by_category.python.total++;
                if (hasTest) coverage.by_category.python.tested++;
            } else if (ext === '.html') {
                coverage.by_category.html.total++;
                if (hasTest) coverage.by_category.html.tested++;
            }
            
            if (hasTest) {
                coverage.tested_files++;
            } else {
                coverage.untested_files.push(sourceFile);
            }
        }
        
        coverage.percentage = Math.round((coverage.tested_files / coverage.total_files) * 100);
        
        return coverage;
    }

    /**
     * Identify gaps in test coverage
     */
    async identifyTestGaps() {
        console.log('🔍 Identifying test gaps...');
        
        const gaps = [];
        const sourceFiles = await this.findSourceFiles();
        
        for (const file of sourceFiles) {
            try {
                const functions = await this.extractFunctions(file);
                const hasTests = await this.checkExistingTests(file);
                
                if (!hasTests && functions.length > 0) {
                    gaps.push({
                        file: file,
                        functions: functions,
                        priority: this.calculatePriority(file, functions),
                        estimated_effort: functions.length * 15 // 15 minutes per function
                    });
                }
            } catch (error) {
                console.warn(`Failed to analyze ${file}:`, error.message);
            }
        }
        
        // Sort by priority
        gaps.sort((a, b) => b.priority - a.priority);
        
        return gaps.slice(0, 10); // Top 10 gaps
    }

    /**
     * Extract functions from source file
     */
    async extractFunctions(filePath) {
        const content = await fs.readFile(filePath, 'utf-8');
        const ext = path.extname(filePath);
        const functions = [];
        
        if (ext === '.js') {
            // Extract JavaScript functions
            const functionMatches = content.match(/(?:function\s+(\w+)|(\w+)\s*[:=]\s*(?:function|\(.*?\)\s*=>)|class\s+(\w+)|(\w+)\s*\([^)]*\)\s*\{)/g) || [];
            functionMatches.forEach(match => {
                const name = match.match(/\w+/)[0];
                if (name !== 'function' && name !== 'class') {
                    functions.push({ name, type: 'function' });
                }
            });
        } else if (ext === '.py') {
            // Extract Python functions
            const functionMatches = content.match(/def\s+(\w+)/g) || [];
            const classMatches = content.match(/class\s+(\w+)/g) || [];
            
            functionMatches.forEach(match => {
                const name = match.match(/def\s+(\w+)/)[1];
                functions.push({ name, type: 'function' });
            });
            
            classMatches.forEach(match => {
                const name = match.match(/class\s+(\w+)/)[1];
                functions.push({ name, type: 'class' });
            });
        }
        
        return functions;
    }

    /**
     * Generate refactor suggestions
     */
    async generateRefactorSuggestions() {
        const suggestions = [];
        
        // Analyze code complexity
        const complexFiles = await this.findComplexFiles();
        complexFiles.forEach(file => {
            suggestions.push({
                type: 'complexity',
                file: file.path,
                issue: 'High complexity detected',
                suggestion: `Consider breaking down ${file.name} into smaller functions`,
                priority: 'medium'
            });
        });
        
        // Check for code duplication
        const duplicates = await this.findCodeDuplication();
        duplicates.forEach(dup => {
            suggestions.push({
                type: 'duplication',
                files: dup.files,
                issue: 'Code duplication detected',
                suggestion: 'Extract common code into shared utility',
                priority: 'low'
            });
        });
        
        // Check for naming conventions
        const namingIssues = await this.checkNamingConventions();
        namingIssues.forEach(issue => {
            suggestions.push({
                type: 'naming',
                file: issue.file,
                issue: issue.problem,
                suggestion: issue.recommendation,
                priority: 'low'
            });
        });
        
        return suggestions.slice(0, 5); // Top 5 suggestions
    }

    /**
     * Utility methods
     */
    async findSourceFiles() {
        const { glob } = require('glob');
        
        const patterns = [
            path.join(this.rootDir, '**/*.js'),
            path.join(this.rootDir, '**/*.py'),
            path.join(this.rootDir, '**/*.html')
        ];
        
        const files = [];
        for (const pattern of patterns) {
            const matches = await glob(pattern, {
                ignore: [
                    '**/node_modules/**',
                    '**/venv/**',
                    '**/.git/**',
                    '**/test*/**',
                    '**/*.test.*',
                    '**/*.spec.*'
                ]
            });
            files.push(...matches);
        }
        
        return files;
    }

    async findTestFiles() {
        const { glob } = require('glob');
        
        const patterns = [
            path.join(this.rootDir, '**/test*.js'),
            path.join(this.rootDir, '**/*.test.js'),
            path.join(this.rootDir, '**/*.spec.js'),
            path.join(this.rootDir, '**/test_*.py'),
            path.join(this.rootDir, '**/*_test.py')
        ];
        
        const files = [];
        for (const pattern of patterns) {
            const matches = await glob(pattern);
            files.push(...matches);
        }
        
        return files;
    }

    hasTestFile(sourceFile, testFiles) {
        const baseName = path.basename(sourceFile, path.extname(sourceFile));
        const testPatterns = [
            `test-${baseName}`,
            `${baseName}.test`,
            `${baseName}.spec`,
            `test_${baseName}`,
            `${baseName}_test`
        ];
        
        return testFiles.some(testFile => {
            const testBaseName = path.basename(testFile, path.extname(testFile));
            return testPatterns.some(pattern => testBaseName.includes(pattern));
        });
    }

    getTestFilePath(sourceFile) {
        const ext = path.extname(sourceFile);
        const baseName = path.basename(sourceFile, ext);
        const testExt = ext === '.py' ? '.py' : '.test.js';
        const testName = ext === '.py' ? `test_${baseName}` : `${baseName}.test`;
        
        return path.join(this.testDir, `${testName}${testExt}`);
    }

    calculatePriority(file, functions) {
        let priority = functions.length; // Base on number of functions
        
        // Higher priority for core files
        if (file.includes('m1k3') || file.includes('ai_inference')) {
            priority *= 2;
        }
        
        // Higher priority for larger files
        try {
            const stats = require('fs').statSync(file);
            priority += Math.min(stats.size / 1000, 10); // Up to 10 bonus points for size
        } catch (error) {
            // Ignore stats errors
        }
        
        return priority;
    }

    async ensureTDDStructure() {
        const dirs = [
            path.join(this.testDir, 'unit'),
            path.join(this.testDir, 'integration'),
            path.join(this.testDir, 'fixtures'),
            path.join(this.testDir, 'mocks'),
            path.join(this.testDir, 'output')
        ];
        
        for (const dir of dirs) {
            try {
                await fs.mkdir(dir, { recursive: true });
            } catch (error) {
                // Directory might already exist
            }
        }
    }

    async checkExistingTests(sourceFile) {
        const testFiles = await this.findTestFiles();
        return this.hasTestFile(sourceFile, testFiles);
    }

    async runTests(options = {}) {
        try {
            const TestRunner = require('./unified-test-runner');
            const runner = new TestRunner({ rootDir: this.rootDir });
            return await runner.runTests(options);
        } catch (error) {
            return {
                failed_count: 1,
                passed_count: 0,
                total_count: 1,
                error: error.message
            };
        }
    }

    async waitForUserInput() {
        return new Promise(resolve => {
            process.stdin.resume();
            process.stdin.setEncoding('utf8');
            process.stdin.once('data', () => {
                process.stdin.pause();
                resolve();
            });
        });
    }

    showFailedTests(failedTests) {
        if (failedTests && failedTests.length > 0) {
            console.log('\n❌ Failed Tests:');
            failedTests.slice(0, 3).forEach(test => {
                console.log(`   • ${test.name}: ${test.error}`);
            });
            if (failedTests.length > 3) {
                console.log(`   ... and ${failedTests.length - 3} more`);
            }
        }
    }

    async findComplexFiles() {
        // Simplified complexity analysis
        const sourceFiles = await this.findSourceFiles();
        const complexFiles = [];
        
        for (const file of sourceFiles.slice(0, 5)) { // Limit for performance
            try {
                const content = await fs.readFile(file, 'utf-8');
                const lines = content.split('\n').length;
                
                if (lines > 500) {
                    complexFiles.push({
                        path: file,
                        name: path.basename(file),
                        lines: lines,
                        complexity: Math.min(lines / 100, 10)
                    });
                }
            } catch (error) {
                // Ignore file read errors
            }
        }
        
        return complexFiles;
    }

    async findCodeDuplication() {
        // Simplified duplication detection
        return []; // Placeholder for more sophisticated analysis
    }

    async checkNamingConventions() {
        // Simplified naming convention check
        return []; // Placeholder for more sophisticated analysis
    }
}

module.exports = TDDHelper;

// CLI usage
if (require.main === module) {
    const helper = new TDDHelper({
        rootDir: path.resolve('../..')
    });

    const command = process.argv[2] || 'init';
    
    async function runCLI() {
        try {
            switch (command) {
                case 'init':
                    await helper.initializeTDD();
                    break;
                    
                case 'cycle':
                    await helper.runTDDCycle();
                    break;
                    
                case 'watch':
                    await helper.startWatchMode();
                    break;
                    
                case 'quick':
                    await helper.runQuickTests();
                    break;
                    
                default:
                    console.log('M1K3 TDD Helper');
                    console.log('===============');
                    console.log('Commands:');
                    console.log('  init   - Initialize TDD workflow');
                    console.log('  cycle  - Run full TDD cycle (Red → Green → Refactor)');
                    console.log('  watch  - Start watch mode for continuous testing');
                    console.log('  quick  - Run quick test subset');
                    break;
            }
        } catch (error) {
            console.error('❌ TDD Helper failed:', error.message);
            process.exit(1);
        }
    }
    
    runCLI();
}