# M1K3 Unified Test Suite

A comprehensive, brand-aligned testing system for the M1K3 AI assistant project with advanced security auditing and Test-Driven Development (TDD) workflow support.

## 🚀 Quick Start

```bash
# Setup the test suite
npm run setup

# Run all tests with HTML report
npm run test:unified

# Run quick validation tests
npm test

# Start TDD workflow
npm run tdd:init
```

## 🎯 Features

### ✅ Unified Test Discovery & Orchestration
- **Automatic Test Discovery**: Intelligently finds and categorizes all test files across the project
- **Multi-Framework Support**: JavaScript (Jest, Playwright, Mocha), Python (pytest, unittest), HTML (QUnit)
- **Parallel Execution**: Runs compatible test categories in parallel for faster execution
- **Smart Categorization**: Unit, integration, visual, performance, security, E2E, and API tests

### 📊 Brand-Aligned Reporting
- **M1K3 Pure Black Design**: Professional reports using the M1K3 design system (#000000 base)
- **Executive Summary**: High-level overview with key metrics and insights
- **Interactive Filtering**: Real-time filtering by category, status, and framework
- **Multi-Format Export**: JSON, CSV, XML, and YAML data export options
- **Visual Test Results**: Screenshot galleries and visual regression reports

### 🔒 Comprehensive Security Auditing
- **Dependency Vulnerabilities**: npm audit, pip safety, and retire.js scanning
- **Code Analysis**: SQL injection, XSS, and command injection detection
- **Secrets Scanning**: API keys, passwords, and sensitive data detection
- **Privacy Compliance**: GDPR, CCPA, and data handling analysis
- **Network Security**: Port scanning and security configuration review

### 🧪 TDD Workflow Support
- **Red-Green-Refactor Cycle**: Guided TDD workflow with phase tracking
- **Test Template Generation**: Auto-generates test stubs for uncovered functions
- **Watch Mode**: Continuous testing with file change detection
- **Coverage Analysis**: Identifies test gaps and coverage improvements
- **Refactoring Suggestions**: Code quality recommendations after green phase

## 📁 Architecture

```
unified-suite/
├── unified-test-runner.js     # Main orchestration engine
├── test-discovery.js          # Test file discovery and categorization  
├── report-generator.js        # Brand-aligned HTML report generation
├── data-exporter.js          # Multi-format data export (JSON/CSV/XML/YAML)
├── security-auditor.js       # Comprehensive security scanning
├── tdd-helper.js             # TDD workflow and watch mode
├── config.json               # Configuration and test categories
├── package.json              # Dependencies and scripts
├── requirements.txt          # Python dependencies
└── output/                   # Generated reports and data
    ├── reports/              # HTML reports
    ├── data/                 # JSON data files  
    └── exports/              # CSV/XML/YAML exports
```

## 🛠 Usage

### Command Line Interface

```bash
# Test Execution
npm run test:unit           # Run unit tests only
npm run test:integration    # Run integration tests
npm run test:visual         # Run visual regression tests  
npm run test:security       # Run security tests
npm run test:performance    # Run performance tests
npm run test:e2e           # Run end-to-end tests
npm run test:api           # Run API tests

# TDD Workflow  
npm run tdd                # Interactive TDD helper
npm run tdd:init           # Initialize TDD workflow
npm run tdd:cycle          # Run Red-Green-Refactor cycle
npm run tdd:watch          # Start watch mode
npm run tdd:quick          # Quick test subset

# Reporting & Analysis
npm run report             # Generate HTML report
npm run export:json        # Export data as JSON
npm run export:csv         # Export data as CSV  
npm run export:xml         # Export data as XML
npm run export:yaml        # Export data as YAML

# Security & Discovery
npm run audit              # Run security audit
npm run discover           # Discover and categorize tests
```

### Programmatic Usage

```javascript
const TestRunner = require('./unified-test-runner');
const SecurityAuditor = require('./security-auditor');  
const TDDHelper = require('./tdd-helper');

// Run unified test suite
const runner = new TestRunner({ rootDir: '../..' });
const results = await runner.runTests({
    categories: ['unit', 'integration'],
    generateReport: true,
    exportFormats: ['json', 'csv']
});

// Security audit
const auditor = new SecurityAuditor({ rootDir: '../..' });
const audit = await auditor.runSecurityAudit();

// TDD workflow
const tdd = new TDDHelper({ rootDir: '../..' });
await tdd.initializeTDD();
await tdd.runTDDCycle();
```

## 📊 Test Categories

| Category | Description | Timeout | Priority |
|----------|-------------|---------|----------|
| **Unit** | Individual component tests | 5s | High |
| **Integration** | Component interaction tests | 15s | High |
| **Visual** | Screenshot & regression tests | 30s | Medium |
| **Performance** | Benchmark & timing tests | 60s | Medium |
| **Security** | Vulnerability & audit tests | 45s | High |
| **E2E** | End-to-end user journeys | 120s | Low |
| **API** | Endpoint & WebSocket tests | 10s | High |

## 🎨 Report Features

### M1K3 Brand Design System
- **Pure Black Foundation**: True black (#000000) background
- **Sophisticated Transparency**: White overlays (2-12%) for depth
- **M1K3 Accent Colors**: Orange (#E25303) for interactive elements
- **Professional Typography**: System fonts optimized for readability

### Interactive Elements
- **Real-time Filtering**: Filter tests by category, status, framework
- **Expandable Details**: Click to view detailed test information
- **Visual Test Gallery**: Screenshot thumbnails with full-size preview
- **Performance Charts**: Visual representation of test timing and metrics

### Executive Summary
- **Key Metrics**: Pass rate, execution time, coverage percentage
- **Trend Analysis**: Comparison with previous test runs
- **Risk Assessment**: Security vulnerabilities and critical failures
- **Recommendations**: Actionable insights for improvement

## 🔒 Security Audit Features

### Vulnerability Scanning
- **Dependency Analysis**: Known vulnerabilities in npm and pip packages
- **Version Checking**: Outdated packages with security implications
- **License Compliance**: License compatibility and legal requirements

### Code Security Analysis
- **Static Analysis**: Code pattern analysis for security issues
- **Injection Detection**: SQL, NoSQL, command, and XSS vulnerabilities  
- **Authentication Issues**: Weak authentication and authorization patterns
- **Data Exposure**: Sensitive data handling and potential leaks

### Infrastructure Security
- **File Permissions**: Overly permissive file and directory permissions
- **Network Configuration**: Open ports and insecure network settings
- **Environment Variables**: Exposed secrets and configuration issues

## 🧪 TDD Features

### Red-Green-Refactor Cycle
1. **RED Phase**: Write failing tests, verify they fail
2. **GREEN Phase**: Write minimal code to pass tests
3. **REFACTOR Phase**: Improve code quality while maintaining tests

### Test Generation
- **Template Creation**: Auto-generates test stubs for JavaScript and Python
- **Coverage Analysis**: Identifies functions and classes without tests
- **Priority Scoring**: Ranks test creation by importance and complexity

### Watch Mode
- **File Monitoring**: Real-time file change detection with chokidar
- **Quick Tests**: Runs fast subset of tests (unit + critical tags)
- **Intelligent Filtering**: Only runs relevant tests based on changed files

## ⚙️ Configuration

The `config.json` file provides comprehensive configuration options:

```json
{
  "test_categories": {
    "unit": { "timeout": 5000, "priority": 1 },
    "integration": { "timeout": 15000, "priority": 2 }
  },
  "reporting": {
    "formats": ["html", "json", "csv"],
    "brand": {
      "colors": { "primary": "#000000", "accent": "#E25303" }
    }
  },
  "security_audit": {
    "scans": ["dependency_vulnerabilities", "code_analysis"],
    "fail_on_severity": "high"
  }
}
```

## 🚀 Integration

### CI/CD Pipeline
```yaml
# .github/workflows/test.yml
- name: Run M1K3 Unified Tests
  run: |
    cd tests/unified-suite
    npm run setup
    npm run test:ci
    npm run audit
```

### Quality Gates
- **Minimum Coverage**: 75% code coverage requirement
- **Maximum Failures**: No more than 5 failed tests
- **Security Threshold**: No high/critical security vulnerabilities
- **Performance Budget**: Tests must complete within timeout limits

## 📈 Performance

### Execution Optimization
- **Parallel Processing**: Compatible test categories run simultaneously
- **Smart Retry Logic**: Automatically retries flaky tests (visual, E2E, performance)
- **Resource Management**: Intelligent resource allocation and cleanup
- **Caching Strategy**: Efficient caching of test results and artifacts

### Scalability
- **Large Codebases**: Handles projects with thousands of test files
- **Memory Efficiency**: Optimized memory usage for long test runs
- **Progress Tracking**: Real-time progress updates and time estimates

## 🤝 Contributing

1. **Test Discovery**: Add new test patterns to `config.json`
2. **Custom Categories**: Extend test categorization logic
3. **Report Themes**: Customize HTML report templates
4. **Security Rules**: Add new security scanning patterns
5. **Framework Support**: Integrate additional testing frameworks

## 📄 License

MIT License - See LICENSE file for details

---

**M1K3 Unified Test Suite** - Comprehensive testing, security, and TDD workflow for the M1K3 AI assistant project.