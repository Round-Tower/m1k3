/**
 * M1K3 Data Exporter
 * Exports test results in multiple formats for analysis and integration
 */

const fs = require('fs').promises;
const path = require('path');

class DataExporter {
    constructor(options = {}) {
        this.outputDir = options.outputDir || path.join(__dirname, 'output/data');
        this.formats = ['json', 'csv', 'xml', 'yaml'];
    }

    /**
     * Export test results in specified format
     */
    async exportData(testResults, format = 'json', options = {}) {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `m1k3-test-data-${timestamp}.${format}`;
        const outputPath = path.join(this.outputDir, filename);

        console.log(`📊 Exporting test data as ${format.toUpperCase()}...`);

        let exportedData;
        
        switch (format.toLowerCase()) {
            case 'json':
                exportedData = await this.exportAsJSON(testResults, options);
                break;
            case 'csv':
                exportedData = await this.exportAsCSV(testResults, options);
                break;
            case 'xml':
                exportedData = await this.exportAsXML(testResults, options);
                break;
            case 'yaml':
                exportedData = await this.exportAsYAML(testResults, options);
                break;
            default:
                throw new Error(`Unsupported export format: ${format}`);
        }

        await fs.writeFile(outputPath, exportedData);
        console.log(`✅ Data exported to: ${outputPath}`);
        
        return outputPath;
    }

    /**
     * Export as structured JSON
     */
    async exportAsJSON(testResults, options = {}) {
        const exportData = {
            export_info: {
                format: 'json',
                version: '1.0.0',
                exported_at: new Date().toISOString(),
                tool: 'M1K3 Unified Test Suite',
                options: options
            },
            test_run: {
                ...testResults,
                analytics: this.generateAnalytics(testResults),
                trends: this.generateTrends(testResults),
                insights: this.generateInsights(testResults)
            }
        };

        return JSON.stringify(exportData, null, options.minified ? 0 : 2);
    }

    /**
     * Export as CSV format
     */
    async exportAsCSV(testResults, options = {}) {
        const tests = testResults.tests || [];
        
        // CSV Headers
        const headers = [
            'test_name',
            'category',
            'status',
            'framework',
            'duration_ms',
            'file_path',
            'error_message',
            'tags',
            'timestamp'
        ];

        // CSV Rows
        const rows = tests.map(test => [
            this.escapeCsvValue(test.name || ''),
            this.escapeCsvValue(test.category || ''),
            this.escapeCsvValue(test.status || ''),
            this.escapeCsvValue(test.framework || ''),
            test.duration || 0,
            this.escapeCsvValue(test.path || ''),
            this.escapeCsvValue(test.error || ''),
            this.escapeCsvValue((test.tags || []).join(';')),
            testResults.metadata?.timestamp || new Date().toISOString()
        ]);

        // Combine headers and rows
        const csvContent = [
            headers.join(','),
            ...rows.map(row => row.join(','))
        ].join('\n');

        return csvContent;
    }

    /**
     * Export as XML format
     */
    async exportAsXML(testResults, options = {}) {
        const { summary, tests, categories, metadata } = testResults;

        const xmlContent = `<?xml version="1.0" encoding="UTF-8"?>
<test_report>
    <metadata>
        <timestamp>${metadata?.timestamp || new Date().toISOString()}</timestamp>
        <tool>M1K3 Unified Test Suite</tool>
        <version>1.0.0</version>
        <environment>
            <platform>${metadata?.environment?.platform || 'unknown'}</platform>
            <node_version>${metadata?.environment?.node_version || 'unknown'}</node_version>
            <memory>${metadata?.environment?.memory || 'unknown'}</memory>
        </environment>
    </metadata>
    
    <summary>
        <total>${summary?.total || 0}</total>
        <passed>${summary?.passed || 0}</passed>
        <failed>${summary?.failed || 0}</failed>
        <skipped>${summary?.skipped || 0}</skipped>
        <duration>${summary?.duration || 0}</duration>
        <success_rate>${summary.total > 0 ? ((summary.passed / summary.total) * 100).toFixed(2) : 0}</success_rate>
    </summary>
    
    <categories>
        ${Object.entries(categories || {}).map(([name, data]) => `
        <category name="${this.escapeXml(name)}">
            <total>${data.total || 0}</total>
            <passed>${data.passed || 0}</passed>
            <failed>${data.failed || 0}</failed>
            <duration>${data.duration || 0}</duration>
        </category>`).join('')}
    </categories>
    
    <tests>
        ${(tests || []).map(test => `
        <test>
            <name>${this.escapeXml(test.name || '')}</name>
            <category>${this.escapeXml(test.category || '')}</category>
            <status>${this.escapeXml(test.status || '')}</status>
            <framework>${this.escapeXml(test.framework || '')}</framework>
            <duration>${test.duration || 0}</duration>
            <path>${this.escapeXml(test.path || '')}</path>
            ${test.error ? `<error>${this.escapeXml(test.error)}</error>` : ''}
        </test>`).join('')}
    </tests>
    
    <analytics>
        ${this.generateXMLAnalytics(testResults)}
    </analytics>
</test_report>`;

        return xmlContent;
    }

    /**
     * Export as YAML format
     */
    async exportAsYAML(testResults, options = {}) {
        try {
            const yaml = require('js-yaml');
            
            const yamlData = {
                export_info: {
                    format: 'yaml',
                    version: '1.0.0',
                    exported_at: new Date().toISOString(),
                    tool: 'M1K3 Unified Test Suite'
                },
                test_run: {
                    ...testResults,
                    analytics: this.generateAnalytics(testResults)
                }
            };

            return yaml.dump(yamlData, {
                indent: 2,
                lineWidth: -1,
                noRefs: true,
                sortKeys: true
            });
        } catch (error) {
            throw new Error(`YAML export failed: ${error.message}`);
        }
    }

    /**
     * Generate analytics for export
     */
    generateAnalytics(testResults) {
        const { summary, tests, categories } = testResults;
        
        if (!tests || tests.length === 0) {
            return {
                success_rate: 0,
                failure_rate: 0,
                avg_duration: 0,
                category_breakdown: {},
                performance_metrics: {},
                trends: {}
            };
        }

        const totalDuration = tests.reduce((sum, test) => sum + (test.duration || 0), 0);
        
        return {
            success_rate: summary.total > 0 ? ((summary.passed / summary.total) * 100).toFixed(2) : 0,
            failure_rate: summary.total > 0 ? ((summary.failed / summary.total) * 100).toFixed(2) : 0,
            avg_duration: tests.length > 0 ? Math.round(totalDuration / tests.length) : 0,
            total_duration: totalDuration,
            category_breakdown: this.generateCategoryBreakdown(categories),
            performance_metrics: this.generatePerformanceMetrics(tests),
            status_distribution: this.generateStatusDistribution(tests),
            framework_usage: this.generateFrameworkUsage(tests),
            trends: this.generateTrends(testResults)
        };
    }

    /**
     * Generate category breakdown analytics
     */
    generateCategoryBreakdown(categories) {
        return Object.entries(categories || {}).reduce((breakdown, [name, data]) => {
            breakdown[name] = {
                total_tests: data.total || 0,
                passed_tests: data.passed || 0,
                failed_tests: data.failed || 0,
                success_rate: data.total > 0 ? ((data.passed / data.total) * 100).toFixed(2) : 0,
                avg_duration: data.tests ? Math.round(data.tests.reduce((sum, t) => sum + (t.duration || 0), 0) / data.tests.length) : 0
            };
            return breakdown;
        }, {});
    }

    /**
     * Generate performance metrics
     */
    generatePerformanceMetrics(tests) {
        const durations = tests.map(t => t.duration || 0).filter(d => d > 0);
        
        if (durations.length === 0) {
            return {
                avg_duration: 0,
                min_duration: 0,
                max_duration: 0,
                median_duration: 0,
                p95_duration: 0
            };
        }

        durations.sort((a, b) => a - b);
        
        return {
            avg_duration: Math.round(durations.reduce((sum, d) => sum + d, 0) / durations.length),
            min_duration: durations[0],
            max_duration: durations[durations.length - 1],
            median_duration: durations[Math.floor(durations.length / 2)],
            p95_duration: durations[Math.floor(durations.length * 0.95)],
            slowest_tests: tests
                .filter(t => t.duration)
                .sort((a, b) => b.duration - a.duration)
                .slice(0, 10)
                .map(t => ({
                    name: t.name,
                    duration: t.duration,
                    category: t.category
                }))
        };
    }

    /**
     * Generate status distribution
     */
    generateStatusDistribution(tests) {
        return tests.reduce((dist, test) => {
            const status = test.status || 'unknown';
            dist[status] = (dist[status] || 0) + 1;
            return dist;
        }, {});
    }

    /**
     * Generate framework usage statistics
     */
    generateFrameworkUsage(tests) {
        return tests.reduce((usage, test) => {
            const framework = test.framework || 'unknown';
            usage[framework] = (usage[framework] || 0) + 1;
            return usage;
        }, {});
    }

    /**
     * Generate trends analysis
     */
    generateTrends(testResults) {
        const { tests, categories } = testResults;
        
        return {
            category_trends: Object.entries(categories || {}).map(([name, data]) => ({
                category: name,
                trend: data.passed > data.failed ? 'positive' : 'negative',
                confidence: data.total > 0 ? (data.passed / data.total) : 0
            })),
            performance_trend: this.calculatePerformanceTrend(tests),
            reliability_indicators: this.calculateReliabilityIndicators(tests)
        };
    }

    /**
     * Generate insights based on test data
     */
    generateInsights(testResults) {
        const { summary, tests, categories } = testResults;
        const insights = [];

        // Success rate insights
        const successRate = summary.total > 0 ? (summary.passed / summary.total) * 100 : 0;
        if (successRate >= 95) {
            insights.push({
                type: 'success',
                message: 'Excellent test coverage with high success rate',
                severity: 'info'
            });
        } else if (successRate < 80) {
            insights.push({
                type: 'warning',
                message: 'Low test success rate indicates potential quality issues',
                severity: 'warning'
            });
        }

        // Performance insights
        const avgDuration = tests.length > 0 ? tests.reduce((sum, t) => sum + (t.duration || 0), 0) / tests.length : 0;
        if (avgDuration > 5000) {
            insights.push({
                type: 'performance',
                message: 'High average test duration may slow down development',
                severity: 'warning'
            });
        }

        // Category insights
        Object.entries(categories || {}).forEach(([name, data]) => {
            if (data.failed > data.passed) {
                insights.push({
                    type: 'category',
                    message: `${name} category has more failures than passes`,
                    severity: 'error',
                    category: name
                });
            }
        });

        return insights;
    }

    /**
     * Utility methods for data formatting
     */
    escapeCsvValue(value) {
        if (value === null || value === undefined) return '';
        const str = String(value);
        if (str.includes(',') || str.includes('"') || str.includes('\n')) {
            return `"${str.replace(/"/g, '""')}"`;
        }
        return str;
    }

    escapeXml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    generateXMLAnalytics(testResults) {
        const analytics = this.generateAnalytics(testResults);
        return `
        <success_rate>${analytics.success_rate}</success_rate>
        <failure_rate>${analytics.failure_rate}</failure_rate>
        <avg_duration>${analytics.avg_duration}</avg_duration>
        <total_duration>${analytics.total_duration}</total_duration>
        `;
    }

    calculatePerformanceTrend(tests) {
        // Simple trend calculation based on test durations
        const avgDuration = tests.length > 0 ? tests.reduce((sum, t) => sum + (t.duration || 0), 0) / tests.length : 0;
        
        if (avgDuration < 1000) return 'fast';
        if (avgDuration < 5000) return 'moderate';
        return 'slow';
    }

    calculateReliabilityIndicators(tests) {
        const totalTests = tests.length;
        const passedTests = tests.filter(t => t.status === 'passed').length;
        const failedTests = tests.filter(t => t.status === 'failed').length;
        
        return {
            stability_score: totalTests > 0 ? (passedTests / totalTests * 100).toFixed(1) : 0,
            failure_density: totalTests > 0 ? (failedTests / totalTests * 100).toFixed(1) : 0,
            test_coverage: totalTests, // Could be enhanced with actual coverage data
            reliability_grade: this.calculateReliabilityGrade(passedTests, totalTests)
        };
    }

    calculateReliabilityGrade(passed, total) {
        if (total === 0) return 'N/A';
        const percentage = (passed / total) * 100;
        
        if (percentage >= 95) return 'A+';
        if (percentage >= 90) return 'A';
        if (percentage >= 85) return 'B+';
        if (percentage >= 80) return 'B';
        if (percentage >= 75) return 'C+';
        if (percentage >= 70) return 'C';
        return 'D';
    }

    /**
     * Export all formats at once
     */
    async exportAllFormats(testResults, options = {}) {
        const results = {};
        
        for (const format of this.formats) {
            try {
                const outputPath = await this.exportData(testResults, format, options);
                results[format] = outputPath;
            } catch (error) {
                console.warn(`⚠️ Failed to export ${format}: ${error.message}`);
                results[format] = { error: error.message };
            }
        }

        return results;
    }

    /**
     * Get export summary
     */
    async getExportSummary(testResults) {
        const analytics = this.generateAnalytics(testResults);
        
        return {
            test_run_summary: {
                total_tests: testResults.summary?.total || 0,
                success_rate: analytics.success_rate,
                avg_duration: analytics.avg_duration,
                categories: Object.keys(testResults.categories || {}),
                frameworks: Object.keys(analytics.framework_usage || {})
            },
            export_options: {
                available_formats: this.formats,
                recommended_format: 'json',
                size_estimates: {
                    json: `~${Math.round(JSON.stringify(testResults).length / 1024)}KB`,
                    csv: '~smaller',
                    xml: '~larger',
                    yaml: '~similar to JSON'
                }
            }
        };
    }
}

module.exports = DataExporter;

// CLI usage
if (require.main === module) {
    const { Command } = require('commander');
    const program = new Command();

    program
        .name('m1k3-export')
        .description('Export M1K3 test data in various formats')
        .version('1.0.0')
        .option('-f, --format <format>', 'Export format (json, csv, xml, yaml)', 'json')
        .option('-i, --input <file>', 'Input JSON file with test results')
        .option('-o, --output <dir>', 'Output directory')
        .option('-a, --all', 'Export in all formats')
        .option('--minified', 'Minify JSON output');

    program.parse();
    const options = program.opts();

    const exporter = new DataExporter({
        outputDir: options.output || path.join(__dirname, 'output/data')
    });

    if (options.input) {
        // Export from file
        fs.readFile(options.input, 'utf-8').then(content => {
            const testResults = JSON.parse(content);
            
            if (options.all) {
                return exporter.exportAllFormats(testResults, options);
            } else {
                return exporter.exportData(testResults, options.format, options);
            }
        }).then(result => {
            console.log('✅ Export completed:', result);
        }).catch(error => {
            console.error('❌ Export failed:', error.message);
            process.exit(1);
        });
    } else {
        // Show export capabilities
        console.log('📊 M1K3 Data Exporter');
        console.log('====================');
        console.log('Available formats:', exporter.formats.join(', '));
        console.log('\nUsage: Provide --input with test results JSON file');
        console.log('Example: node data-exporter.js --input results.json --format csv');
    }
}