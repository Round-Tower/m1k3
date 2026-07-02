/**
 * M1K3 Security Auditor
 * Comprehensive security analysis for code, dependencies, and configurations
 */

const fs = require('fs').promises;
const path = require('path');
const { spawn } = require('child_process');
const crypto = require('crypto');

class SecurityAuditor {
    constructor(options = {}) {
        this.rootDir = options.rootDir || path.resolve('../..');
        this.outputDir = options.outputDir || path.join(__dirname, 'output/data');
        this.config = options.config || this.getDefaultConfig();
        
        this.findings = {
            critical: [],
            high: [],
            medium: [],
            low: [],
            info: []
        };
        
        this.scanResults = {
            timestamp: new Date().toISOString(),
            summary: {
                total_files_scanned: 0,
                total_vulnerabilities: 0,
                critical_count: 0,
                high_count: 0,
                medium_count: 0,
                low_count: 0
            },
            scans: {
                dependency_audit: null,
                code_analysis: null,
                file_permissions: null,
                secrets_scan: null,
                network_security: null,
                privacy_compliance: null
            }
        };
    }

    /**
     * Get default security configuration
     */
    getDefaultConfig() {
        return {
            enableDependencyAudit: true,
            enableCodeAnalysis: true,
            enableSecretsScanning: true,
            enableFilePermissions: true,
            enableNetworkSecurity: true,
            enablePrivacyCompliance: true,
            
            // File patterns to exclude
            excludePatterns: [
                '**/node_modules/**',
                '**/venv/**',
                '**/.git/**',
                '**/dist/**',
                '**/build/**',
                '**/*.min.js',
                '**/*.log'
            ],
            
            // Secret patterns to detect
            secretPatterns: [
                {
                    name: 'API Key',
                    pattern: /api[_-]?key[_-]?[:=]\s*['"]\w{16,}['"]/gi,
                    severity: 'high'
                },
                {
                    name: 'JWT Token',
                    pattern: /jwt[_-]?token[_-]?[:=]\s*['"]\w+\.\w+\.\w+['"]/gi,
                    severity: 'high'
                },
                {
                    name: 'Password',
                    pattern: /password[_-]?[:=]\s*['"][^'"]{8,}['"]/gi,
                    severity: 'critical'
                },
                {
                    name: 'Private Key',
                    pattern: /-----BEGIN\s+(?:RSA\s+)?PRIVATE\s+KEY-----/gi,
                    severity: 'critical'
                },
                {
                    name: 'AWS Access Key',
                    pattern: /AKIA[0-9A-Z]{16}/g,
                    severity: 'critical'
                },
                {
                    name: 'GitHub Token',
                    pattern: /ghp_[A-Za-z0-9]{36}/g,
                    severity: 'high'
                }
            ]
        };
    }

    /**
     * Run comprehensive security audit
     */
    async runSecurityAudit() {
        console.log('🔒 Starting M1K3 Security Audit...');
        console.log('===================================');

        try {
            // Dependency audit
            if (this.config.enableDependencyAudit) {
                console.log('📦 Running dependency audit...');
                this.scanResults.scans.dependency_audit = await this.auditDependencies();
            }

            // Code analysis
            if (this.config.enableCodeAnalysis) {
                console.log('🔍 Analyzing code for vulnerabilities...');
                this.scanResults.scans.code_analysis = await this.analyzeCode();
            }

            // Secrets scanning
            if (this.config.enableSecretsScanning) {
                console.log('🔐 Scanning for exposed secrets...');
                this.scanResults.scans.secrets_scan = await this.scanForSecrets();
            }

            // File permissions
            if (this.config.enableFilePermissions) {
                console.log('📂 Checking file permissions...');
                this.scanResults.scans.file_permissions = await this.checkFilePermissions();
            }

            // Network security
            if (this.config.enableNetworkSecurity) {
                console.log('🌐 Analyzing network security...');
                this.scanResults.scans.network_security = await this.analyzeNetworkSecurity();
            }

            // Privacy compliance
            if (this.config.enablePrivacyCompliance) {
                console.log('🛡️ Checking privacy compliance...');
                this.scanResults.scans.privacy_compliance = await this.checkPrivacyCompliance();
            }

            // Calculate summary
            this.calculateSummary();

            // Generate report
            const reportPath = await this.generateSecurityReport();

            this.logResults();
            
            return {
                results: this.scanResults,
                findings: this.findings,
                reportPath: reportPath
            };

        } catch (error) {
            console.error('❌ Security audit failed:', error);
            throw error;
        }
    }

    /**
     * Audit npm/pip dependencies for known vulnerabilities
     */
    async auditDependencies() {
        const results = {
            npm_audit: null,
            pip_audit: null,
            yarn_audit: null,
            vulnerabilities: [],
            recommendations: []
        };

        try {
            // Check for package.json files
            const packageJsonFiles = await this.findFiles('**/package.json');
            
            for (const packageFile of packageJsonFiles) {
                if (packageFile.includes('node_modules')) continue;
                
                console.log(`   📦 Auditing ${packageFile}...`);
                
                try {
                    const npmAudit = await this.runNpmAudit(path.dirname(packageFile));
                    if (npmAudit) {
                        results.npm_audit = npmAudit;
                        this.processNpmAuditResults(npmAudit);
                    }
                } catch (error) {
                    console.warn(`   ⚠️ NPM audit failed for ${packageFile}: ${error.message}`);
                }
            }

            // Check for requirements.txt files
            const requirementFiles = await this.findFiles('**/requirements.txt');
            
            for (const reqFile of requirementFiles) {
                console.log(`   🐍 Auditing ${reqFile}...`);
                
                try {
                    const pipAudit = await this.runPipAudit(reqFile);
                    if (pipAudit) {
                        results.pip_audit = pipAudit;
                        this.processPipAuditResults(pipAudit);
                    }
                } catch (error) {
                    console.warn(`   ⚠️ Pip audit failed for ${reqFile}: ${error.message}`);
                }
            }

        } catch (error) {
            console.warn('⚠️ Dependency audit partially failed:', error.message);
        }

        return results;
    }

    /**
     * Run npm audit
     */
    async runNpmAudit(packageDir) {
        return new Promise((resolve) => {
            const auditProcess = spawn('npm', ['audit', '--json'], {
                cwd: packageDir,
                stdio: 'pipe'
            });

            let stdout = '';
            let stderr = '';

            auditProcess.stdout.on('data', (data) => stdout += data.toString());
            auditProcess.stderr.on('data', (data) => stderr += data.toString());

            auditProcess.on('close', (code) => {
                try {
                    if (stdout) {
                        const auditResult = JSON.parse(stdout);
                        resolve(auditResult);
                    } else {
                        resolve(null);
                    }
                } catch (error) {
                    resolve(null);
                }
            });

            auditProcess.on('error', () => resolve(null));
        });
    }

    /**
     * Run pip safety check (requires safety package)
     */
    async runPipAudit(requirementsFile) {
        return new Promise((resolve) => {
            const safetyProcess = spawn('python3', ['-m', 'safety', 'check', '-r', requirementsFile, '--json'], {
                stdio: 'pipe'
            });

            let stdout = '';
            
            safetyProcess.stdout.on('data', (data) => stdout += data.toString());
            
            safetyProcess.on('close', (code) => {
                try {
                    if (stdout) {
                        const safetyResult = JSON.parse(stdout);
                        resolve(safetyResult);
                    } else {
                        resolve(null);
                    }
                } catch (error) {
                    resolve(null);
                }
            });

            safetyProcess.on('error', () => resolve(null));
        });
    }

    /**
     * Analyze code for common security vulnerabilities
     */
    async analyzeCode() {
        const results = {
            sql_injection: [],
            xss_vulnerabilities: [],
            path_traversal: [],
            command_injection: [],
            unsafe_eval: [],
            cors_issues: []
        };

        const codeFiles = await this.findFiles('**/*.{js,py,html,php}');
        this.scanResults.summary.total_files_scanned += codeFiles.length;

        for (const filePath of codeFiles) {
            if (this.shouldExcludeFile(filePath)) continue;

            try {
                const content = await fs.readFile(filePath, 'utf-8');
                
                // SQL Injection patterns
                const sqlPatterns = [
                    /query\s*\+\s*['"]/gi,
                    /execute\s*\(\s*['"]/gi,
                    /\$\{.*\}\s*INTO/gi
                ];
                
                for (const pattern of sqlPatterns) {
                    if (pattern.test(content)) {
                        results.sql_injection.push({
                            file: filePath,
                            pattern: pattern.toString(),
                            severity: 'high'
                        });
                        this.addFinding('high', 'SQL Injection Risk', `Potential SQL injection in ${filePath}`, filePath);
                    }
                }

                // XSS patterns
                const xssPatterns = [
                    /innerHTML\s*=\s*[^'"]/gi,
                    /document\.write\s*\(/gi,
                    /eval\s*\(/gi
                ];

                for (const pattern of xssPatterns) {
                    if (pattern.test(content)) {
                        results.xss_vulnerabilities.push({
                            file: filePath,
                            pattern: pattern.toString(),
                            severity: 'medium'
                        });
                        this.addFinding('medium', 'XSS Vulnerability', `Potential XSS vulnerability in ${filePath}`, filePath);
                    }
                }

                // Command injection
                const cmdPatterns = [
                    /exec\s*\(\s*[^'"]/gi,
                    /system\s*\(\s*[^'"]/gi,
                    /spawn\s*\(\s*[^'"]/gi
                ];

                for (const pattern of cmdPatterns) {
                    if (pattern.test(content)) {
                        results.command_injection.push({
                            file: filePath,
                            pattern: pattern.toString(),
                            severity: 'high'
                        });
                        this.addFinding('high', 'Command Injection Risk', `Potential command injection in ${filePath}`, filePath);
                    }
                }

            } catch (error) {
                console.warn(`   ⚠️ Failed to analyze ${filePath}: ${error.message}`);
            }
        }

        return results;
    }

    /**
     * Scan for exposed secrets and sensitive information
     */
    async scanForSecrets() {
        const results = {
            secrets_found: [],
            suspicious_files: [],
            recommendations: []
        };

        const allFiles = await this.findFiles('**/*');
        
        for (const filePath of allFiles) {
            if (this.shouldExcludeFile(filePath)) continue;
            
            try {
                const content = await fs.readFile(filePath, 'utf-8');
                
                // Check each secret pattern
                for (const secretPattern of this.config.secretPatterns) {
                    const matches = content.match(secretPattern.pattern);
                    
                    if (matches) {
                        for (const match of matches) {
                            results.secrets_found.push({
                                file: filePath,
                                type: secretPattern.name,
                                match: match.substring(0, 50) + '...',
                                severity: secretPattern.severity,
                                line: this.findLineNumber(content, match)
                            });
                            
                            this.addFinding(
                                secretPattern.severity, 
                                `Exposed ${secretPattern.name}`, 
                                `Found ${secretPattern.name} in ${filePath}`, 
                                filePath
                            );
                        }
                    }
                }

                // Check for suspicious file patterns
                if (this.isSuspiciousFile(filePath, content)) {
                    results.suspicious_files.push({
                        file: filePath,
                        reason: 'Contains sensitive keywords',
                        severity: 'low'
                    });
                }

            } catch (error) {
                // File might be binary or inaccessible
                continue;
            }
        }

        return results;
    }

    /**
     * Check file permissions for security issues
     */
    async checkFilePermissions() {
        const results = {
            world_writable: [],
            executable_configs: [],
            sensitive_permissions: []
        };

        const allFiles = await this.findFiles('**/*');
        
        for (const filePath of allFiles) {
            if (this.shouldExcludeFile(filePath)) continue;
            
            try {
                const stats = await fs.stat(filePath);
                const mode = stats.mode;
                
                // Check for world-writable files
                if (mode & 0o002) {
                    results.world_writable.push({
                        file: filePath,
                        permissions: (mode & parseInt('777', 8)).toString(8),
                        severity: 'medium'
                    });
                    this.addFinding('medium', 'World-Writable File', `File ${filePath} is world-writable`, filePath);
                }

                // Check for executable config files
                if ((mode & 0o111) && this.isConfigFile(filePath)) {
                    results.executable_configs.push({
                        file: filePath,
                        permissions: (mode & parseInt('777', 8)).toString(8),
                        severity: 'low'
                    });
                    this.addFinding('low', 'Executable Config File', `Config file ${filePath} is executable`, filePath);
                }

            } catch (error) {
                continue;
            }
        }

        return results;
    }

    /**
     * Analyze network security configurations
     */
    async analyzeNetworkSecurity() {
        const results = {
            cors_issues: [],
            insecure_protocols: [],
            missing_security_headers: [],
            recommendations: []
        };

        // Look for CORS configurations
        const webFiles = await this.findFiles('**/*.{js,py,php,html}');
        
        for (const filePath of webFiles) {
            if (this.shouldExcludeFile(filePath)) continue;
            
            try {
                const content = await fs.readFile(filePath, 'utf-8');
                
                // Check for permissive CORS
                if (content.includes('Access-Control-Allow-Origin: *')) {
                    results.cors_issues.push({
                        file: filePath,
                        issue: 'Permissive CORS policy',
                        severity: 'medium'
                    });
                    this.addFinding('medium', 'Permissive CORS', `Permissive CORS policy in ${filePath}`, filePath);
                }

                // Check for insecure protocols
                if (content.includes('http://') && !content.includes('localhost')) {
                    results.insecure_protocols.push({
                        file: filePath,
                        issue: 'HTTP instead of HTTPS',
                        severity: 'low'
                    });
                    this.addFinding('low', 'Insecure Protocol', `HTTP usage in ${filePath}`, filePath);
                }

            } catch (error) {
                continue;
            }
        }

        return results;
    }

    /**
     * Check privacy compliance
     */
    async checkPrivacyCompliance() {
        const results = {
            data_collection: [],
            third_party_tracking: [],
            privacy_policy_issues: [],
            recommendations: []
        };

        const webFiles = await this.findFiles('**/*.{js,html,php}');
        
        for (const filePath of webFiles) {
            if (this.shouldExcludeFile(filePath)) continue;
            
            try {
                const content = await fs.readFile(filePath, 'utf-8');
                
                // Check for tracking scripts
                const trackingPatterns = [
                    /google-analytics/gi,
                    /googletagmanager/gi,
                    /facebook\.com\/tr/gi,
                    /connect\.facebook/gi
                ];

                for (const pattern of trackingPatterns) {
                    if (pattern.test(content)) {
                        results.third_party_tracking.push({
                            file: filePath,
                            tracker: pattern.toString(),
                            severity: 'info'
                        });
                        this.addFinding('info', 'Third-party Tracking', `Third-party tracker found in ${filePath}`, filePath);
                    }
                }

                // Check for data collection patterns
                if (content.includes('localStorage') || content.includes('sessionStorage')) {
                    results.data_collection.push({
                        file: filePath,
                        type: 'Local storage usage',
                        severity: 'info'
                    });
                }

            } catch (error) {
                continue;
            }
        }

        return results;
    }

    /**
     * Process npm audit results
     */
    processNpmAuditResults(auditResult) {
        if (auditResult.vulnerabilities) {
            Object.entries(auditResult.vulnerabilities).forEach(([pkg, vuln]) => {
                const severity = vuln.severity || 'medium';
                this.addFinding(
                    severity,
                    'Dependency Vulnerability',
                    `${pkg}: ${vuln.title || 'Unknown vulnerability'}`,
                    `package: ${pkg}`
                );
            });
        }
    }

    /**
     * Process pip audit results
     */
    processPipAuditResults(auditResult) {
        if (Array.isArray(auditResult)) {
            auditResult.forEach(vuln => {
                this.addFinding(
                    'high',
                    'Python Dependency Vulnerability',
                    `${vuln.package}: ${vuln.vulnerability}`,
                    `package: ${vuln.package}`
                );
            });
        }
    }

    /**
     * Add a security finding
     */
    addFinding(severity, title, description, location) {
        const finding = {
            severity,
            title,
            description,
            location,
            timestamp: new Date().toISOString()
        };

        this.findings[severity].push(finding);
    }

    /**
     * Calculate summary statistics
     */
    calculateSummary() {
        this.scanResults.summary.critical_count = this.findings.critical.length;
        this.scanResults.summary.high_count = this.findings.high.length;
        this.scanResults.summary.medium_count = this.findings.medium.length;
        this.scanResults.summary.low_count = this.findings.low.length;
        
        this.scanResults.summary.total_vulnerabilities = 
            this.scanResults.summary.critical_count +
            this.scanResults.summary.high_count +
            this.scanResults.summary.medium_count +
            this.scanResults.summary.low_count;
    }

    /**
     * Generate security report
     */
    async generateSecurityReport() {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportPath = path.join(this.outputDir, `security-audit-${timestamp}.json`);
        
        const report = {
            audit_info: {
                timestamp: this.scanResults.timestamp,
                tool: 'M1K3 Security Auditor',
                version: '1.0.0',
                root_directory: this.rootDir
            },
            summary: this.scanResults.summary,
            scan_results: this.scanResults.scans,
            findings: this.findings,
            recommendations: this.generateRecommendations()
        };

        await fs.writeFile(reportPath, JSON.stringify(report, null, 2));
        return reportPath;
    }

    /**
     * Generate security recommendations
     */
    generateRecommendations() {
        const recommendations = [];

        if (this.findings.critical.length > 0) {
            recommendations.push({
                priority: 'critical',
                title: 'Address Critical Vulnerabilities',
                description: 'Immediately fix all critical security vulnerabilities before deployment.'
            });
        }

        if (this.findings.high.length > 0) {
            recommendations.push({
                priority: 'high',
                title: 'Fix High-Severity Issues',
                description: 'Address high-severity vulnerabilities within 24-48 hours.'
            });
        }

        recommendations.push({
            priority: 'medium',
            title: 'Regular Security Audits',
            description: 'Run security audits regularly as part of your CI/CD pipeline.'
        });

        recommendations.push({
            priority: 'low',
            title: 'Security Best Practices',
            description: 'Implement security headers, use HTTPS, and follow secure coding practices.'
        });

        return recommendations;
    }

    /**
     * Utility methods
     */
    async findFiles(pattern) {
        const { glob } = require('glob');
        return await glob(pattern, {
            cwd: this.rootDir,
            ignore: this.config.excludePatterns,
            absolute: true
        });
    }

    shouldExcludeFile(filePath) {
        return this.config.excludePatterns.some(pattern => {
            const regex = new RegExp(pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*'));
            return regex.test(filePath);
        });
    }

    isConfigFile(filePath) {
        const configExtensions = ['.conf', '.config', '.ini', '.cfg', '.env'];
        return configExtensions.some(ext => filePath.endsWith(ext));
    }

    isSuspiciousFile(filePath, content) {
        const suspiciousKeywords = ['password', 'secret', 'key', 'token', 'credential'];
        const fileName = path.basename(filePath).toLowerCase();
        
        return suspiciousKeywords.some(keyword => 
            fileName.includes(keyword) || content.toLowerCase().includes(keyword)
        );
    }

    findLineNumber(content, searchString) {
        const lines = content.split('\n');
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes(searchString)) {
                return i + 1;
            }
        }
        return 1;
    }

    logResults() {
        const { summary } = this.scanResults;
        
        console.log('\n🔒 Security Audit Results');
        console.log('=========================');
        console.log(`📁 Files scanned: ${summary.total_files_scanned}`);
        console.log(`🚨 Total vulnerabilities: ${summary.total_vulnerabilities}`);
        console.log(`⚠️  Critical: ${summary.critical_count}`);
        console.log(`⚠️  High: ${summary.high_count}`);
        console.log(`⚠️  Medium: ${summary.medium_count}`);
        console.log(`⚠️  Low: ${summary.low_count}`);

        if (summary.critical_count > 0 || summary.high_count > 0) {
            console.log('\n🚨 ACTION REQUIRED: Critical or high-severity vulnerabilities found!');
        } else if (summary.medium_count > 0) {
            console.log('\n⚠️ ATTENTION: Medium-severity vulnerabilities found');
        } else {
            console.log('\n✅ No critical security issues detected');
        }
    }
}

module.exports = SecurityAuditor;

// CLI usage
if (require.main === module) {
    const { Command } = require('commander');
    const program = new Command();

    program
        .name('m1k3-security')
        .description('M1K3 Security Auditor')
        .version('1.0.0')
        .option('-r, --root <dir>', 'Root directory to scan')
        .option('-o, --output <dir>', 'Output directory for reports')
        .option('--skip-deps', 'Skip dependency audit')
        .option('--skip-code', 'Skip code analysis')
        .option('--skip-secrets', 'Skip secrets scanning')
        .option('--verbose', 'Verbose output');

    program.parse();
    const options = program.opts();

    const config = {
        enableDependencyAudit: !options.skipDeps,
        enableCodeAnalysis: !options.skipCode,
        enableSecretsScanning: !options.skipSecrets,
        enableFilePermissions: true,
        enableNetworkSecurity: true,
        enablePrivacyCompliance: true
    };

    const auditor = new SecurityAuditor({
        rootDir: options.root || process.cwd(),
        outputDir: options.output,
        config: config
    });

    auditor.runSecurityAudit().then(results => {
        console.log(`\n📊 Security report saved: ${results.reportPath}`);
        
        // Exit with error code if critical or high vulnerabilities found
        if (results.results.summary.critical_count > 0 || results.results.summary.high_count > 0) {
            process.exit(1);
        }
    }).catch(error => {
        console.error('❌ Security audit failed:', error.message);
        process.exit(1);
    });
}