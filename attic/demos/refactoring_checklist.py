#!/usr/bin/env python3
"""
M1K3 Refactoring Checklist Generator
Analyzes demo results and generates prioritized refactoring tasks
"""

import sys
import json
import time
from pathlib import Path
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

@dataclass
class RefactoringTask:
    """Represents a refactoring task"""
    priority: str  # Critical, High, Medium, Low
    category: str  # Performance, Architecture, Code Quality, Features
    title: str
    description: str
    files_involved: List[str]
    effort_estimate: str  # Small, Medium, Large
    blockers: List[str]
    benefits: List[str]

class RefactoringAnalyzer:
    """Analyzes M1K3 codebase and generates refactoring recommendations"""
    
    def __init__(self):
        self.tasks = []
        self.demo_results = {}
        self.benchmark_results = {}
        
        print("🔧 M1K3 REFACTORING CHECKLIST GENERATOR")
        print("=" * 50)
        print("Analyzing codebase and demo results for refactoring priorities")
        print()
    
    def load_demo_results(self):
        """Load results from demo runs"""
        results_dir = Path(__file__).parent
        
        # Load test results
        test_results_file = results_dir / "test_results.json"
        if test_results_file.exists():
            with open(test_results_file) as f:
                self.demo_results['tests'] = json.load(f)
        
        # Load benchmark results  
        benchmark_file = results_dir / "benchmarks" / "startup_benchmark_results.json"
        if benchmark_file.exists():
            with open(benchmark_file) as f:
                self.benchmark_results = json.load(f)
        
        print(f"📊 Loaded demo results: {len(self.demo_results)} categories")
        print(f"📊 Loaded benchmark data: {bool(self.benchmark_results)}")
    
    def analyze_performance_issues(self):
        """Analyze performance-related refactoring needs"""
        
        # Check startup performance
        if self.benchmark_results:
            summary = self.benchmark_results.get('summary', {})
            improvement = summary.get('overall_improvement_percent', 0)
            
            if improvement < 50:  # Target was 50%+ improvement
                self.tasks.append(RefactoringTask(
                    priority="Critical",
                    category="Performance", 
                    title="Optimize Startup Performance",
                    description=f"Current startup improvement is {improvement:.1f}%, below 50% target. Need deeper async optimization.",
                    files_involved=[
                        "src/engines/ai/async_model_loader.py",
                        "src/utils/performance/startup_optimizer.py", 
                        "src/cli/cli_core.py"
                    ],
                    effort_estimate="Large",
                    blockers=["Model loading architecture", "Dependency initialization order"],
                    benefits=["Faster user experience", "Better device compatibility", "Reduced resource usage"]
                ))
            
            # Memory usage analysis
            memory_change = summary.get('memory_change_percent', 0)
            if abs(memory_change) > 20:  # Significant memory change
                priority = "High" if memory_change > 0 else "Medium"
                self.tasks.append(RefactoringTask(
                    priority=priority,
                    category="Performance",
                    title="Optimize Memory Usage",
                    description=f"Memory usage changed by {memory_change:+.1f}% during optimization. Review memory patterns.",
                    files_involved=[
                        "src/engines/ai/async_model_loader.py",
                        "src/utils/performance/device_detector.py"
                    ],
                    effort_estimate="Medium",
                    blockers=["Model loading strategy"],
                    benefits=["Better resource efficiency", "Improved device compatibility"]
                ))
    
    def analyze_architecture_issues(self):
        """Analyze architectural refactoring needs"""
        
        # CLI architecture improvements
        self.tasks.append(RefactoringTask(
            priority="High",
            category="Architecture",
            title="Modernize CLI Architecture", 
            description="Current CLI mixing concerns. Separate input handling, command processing, and output formatting.",
            files_involved=[
                "src/cli/cli_core.py",
                "cli.py",
                "m1k3.py"
            ],
            effort_estimate="Large",
            blockers=["Backward compatibility requirements"],
            benefits=["Improved testability", "Better separation of concerns", "Easier feature addition"]
        ))
        
        # Error handling standardization
        self.tasks.append(RefactoringTask(
            priority="Medium",
            category="Architecture",
            title="Standardize Error Handling",
            description="Inconsistent error handling across components. Implement unified error handling strategy.",
            files_involved=[
                "src/engines/ai/*.py",
                "src/engines/voice/*.py", 
                "src/utils/performance/*.py"
            ],
            effort_estimate="Medium",
            blockers=["Legacy error handling patterns"],
            benefits=["Better debugging", "Consistent user experience", "Improved reliability"]
        ))
        
        # Configuration management
        self.tasks.append(RefactoringTask(
            priority="Medium",
            category="Architecture",
            title="Centralize Configuration Management",
            description="Configuration scattered across multiple files. Create unified config system.",
            files_involved=[
                "src/config/",
                "src/cli/cli_core.py",
                "Various component files"
            ],
            effort_estimate="Medium",
            blockers=["Existing hardcoded values"],
            benefits=["Easier deployment", "Better testing", "Simplified maintenance"]
        ))
    
    def analyze_code_quality_issues(self):
        """Analyze code quality refactoring needs"""
        
        # Import path issues
        self.tasks.append(RefactoringTask(
            priority="High",
            category="Code Quality",
            title="Fix Import Path Dependencies",
            description="Demo scripts require sys.path manipulation. Restructure package imports.",
            files_involved=[
                "demos/*.py",
                "src/__init__.py",
                "setup.py"
            ],
            effort_estimate="Small",
            blockers=["Package structure decisions"],
            benefits=["Cleaner imports", "Better IDE support", "Easier testing"]
        ))
        
        # Type annotations
        self.tasks.append(RefactoringTask(
            priority="Medium", 
            category="Code Quality",
            title="Add Comprehensive Type Annotations",
            description="Many functions lack type hints. Add types for better IDE support and catching bugs.",
            files_involved=[
                "src/engines/**/*.py",
                "src/utils/**/*.py",
                "src/cli/*.py"
            ],
            effort_estimate="Large",
            blockers=["Complex generic types"],
            benefits=["Better IDE support", "Catch type errors early", "Improved documentation"]
        ))
        
        # Docstring standardization
        self.tasks.append(RefactoringTask(
            priority="Low",
            category="Code Quality", 
            title="Standardize Documentation",
            description="Inconsistent docstring formats. Standardize on Google/Sphinx format.",
            files_involved=[
                "All Python files"
            ],
            effort_estimate="Medium",
            blockers=["Documentation format choice"],
            benefits=["Better auto-generated docs", "Consistent documentation", "Improved maintainability"]
        ))
    
    def analyze_feature_gaps(self):
        """Analyze missing features that need implementation"""
        
        # Mobile platform support
        self.tasks.append(RefactoringTask(
            priority="Critical",
            category="Features",
            title="Implement Mobile Platform Support", 
            description="iOS and Android identified as core markets but not implemented. Create mobile deployment strategy.",
            files_involved=[
                "New mobile/ directory",
                "pwa-deployment/ (enhancement)",
                "src/utils/performance/device_detector.py"
            ],
            effort_estimate="Large",
            blockers=["Platform-specific dependencies", "Mobile AI model optimization"],
            benefits=["Expand market reach", "Mobile-first user experience", "Cross-platform consistency"]
        ))
        
        # Voice input reliability 
        self.tasks.append(RefactoringTask(
            priority="High",
            category="Features",
            title="Fix macOS Voice Input Failures",
            description="Agent identified macOS voice input failures. Debug and fix STT system reliability.",
            files_involved=[
                "src/engines/voice/stt_engine.py",
                "src/utils/audio/*.py"
            ],
            effort_estimate="Medium", 
            blockers=["macOS permission issues", "Audio device compatibility"],
            benefits=["Reliable voice input", "Better user experience", "Platform consistency"]
        ))
        
        # Enhanced avatar system
        self.tasks.append(RefactoringTask(
            priority="Medium",
            category="Features",
            title="Enhance Avatar Emotion System",
            description="Current avatar system basic. Add more sophisticated emotion tracking and expressions.",
            files_involved=[
                "src/avatar/*.py",
                "avatar_dashboard.html",
                "WebSocket communication"
            ],
            effort_estimate="Medium",
            blockers=["Animation framework choice"],
            benefits=["More engaging user experience", "Better emotional feedback", "Enhanced interactivity"]
        ))
    
    def analyze_testing_gaps(self):
        """Analyze testing and quality assurance needs"""
        
        # Unit test coverage
        self.tasks.append(RefactoringTask(
            priority="High",
            category="Code Quality",
            title="Expand Unit Test Coverage",
            description="Current tests mostly integration. Need comprehensive unit tests for core components.",
            files_involved=[
                "tests/ (new directory)",
                "All src/ components"
            ],
            effort_estimate="Large",
            blockers=["Testing framework setup", "Mock dependencies"],
            benefits=["Catch regressions early", "Better refactoring confidence", "Improved code quality"]
        ))
        
        # Performance regression testing
        self.tasks.append(RefactoringTask(
            priority="Medium",
            category="Code Quality",
            title="Implement Performance Regression Testing",
            description="No automated performance testing. Add CI/CD performance benchmarks.",
            files_involved=[
                "demos/benchmarks/",
                ".github/workflows/",
                "Performance test suite"
            ],
            effort_estimate="Medium",
            blockers=["CI/CD performance measurement setup"],
            benefits=["Prevent performance regressions", "Track optimization progress", "Data-driven improvements"]
        ))
    
    def prioritize_tasks(self):
        """Sort tasks by priority and impact"""
        priority_order = {"Critical": 0, "High": 1, "Medium": 2, "Low": 3}
        
        self.tasks.sort(key=lambda x: (
            priority_order[x.priority],
            len(x.benefits),  # More benefits = higher priority
            x.effort_estimate == "Small"  # Prefer small effort when tied
        ))
    
    def generate_report(self):
        """Generate comprehensive refactoring report"""
        
        # Load data
        self.load_demo_results()
        
        # Analyze different aspects
        self.analyze_performance_issues()
        self.analyze_architecture_issues() 
        self.analyze_code_quality_issues()
        self.analyze_feature_gaps()
        self.analyze_testing_gaps()
        
        # Prioritize
        self.prioritize_tasks()
        
        # Generate reports
        self._generate_console_report()
        self._generate_json_report()
        self._generate_markdown_report()
        
        print(f"\n✅ Generated refactoring checklist with {len(self.tasks)} tasks")
        print(f"📄 Reports saved in {Path(__file__).parent}/")
    
    def _generate_console_report(self):
        """Generate console output report"""
        
        print(f"\n📋 REFACTORING PRIORITY CHECKLIST")
        print("=" * 50)
        
        priority_counts = {}
        for task in self.tasks:
            priority_counts[task.priority] = priority_counts.get(task.priority, 0) + 1
        
        print(f"Summary: {sum(priority_counts.values())} tasks")
        for priority, count in priority_counts.items():
            print(f"  {priority}: {count} tasks")
        
        print("\nTop Priority Tasks:")
        print("-" * 30)
        
        for i, task in enumerate(self.tasks[:5], 1):
            print(f"\n{i}. [{task.priority}] {task.title}")
            print(f"   Category: {task.category}")
            print(f"   Effort: {task.effort_estimate}")
            print(f"   Files: {len(task.files_involved)} files affected")
            print(f"   Benefits: {', '.join(task.benefits[:2])}{'...' if len(task.benefits) > 2 else ''}")
        
        print(f"\n... and {len(self.tasks) - 5} more tasks in detailed report")
    
    def _generate_json_report(self):
        """Generate JSON report for programmatic use"""
        
        report = {
            'generated_at': time.time(),
            'summary': {
                'total_tasks': len(self.tasks),
                'by_priority': {},
                'by_category': {},
                'by_effort': {}
            },
            'tasks': []
        }
        
        # Calculate summaries
        for task in self.tasks:
            # Priority breakdown
            report['summary']['by_priority'][task.priority] = \
                report['summary']['by_priority'].get(task.priority, 0) + 1
            
            # Category breakdown  
            report['summary']['by_category'][task.category] = \
                report['summary']['by_category'].get(task.category, 0) + 1
            
            # Effort breakdown
            report['summary']['by_effort'][task.effort_estimate] = \
                report['summary']['by_effort'].get(task.effort_estimate, 0) + 1
            
            # Task details
            report['tasks'].append({
                'priority': task.priority,
                'category': task.category,
                'title': task.title,
                'description': task.description,
                'files_involved': task.files_involved,
                'effort_estimate': task.effort_estimate,
                'blockers': task.blockers,
                'benefits': task.benefits
            })
        
        # Include demo/benchmark data context
        if self.demo_results:
            report['demo_context'] = self.demo_results
        if self.benchmark_results:
            report['benchmark_context'] = self.benchmark_results
        
        # Save JSON report
        json_file = Path(__file__).parent / "refactoring_checklist.json"
        with open(json_file, 'w') as f:
            json.dump(report, f, indent=2)
    
    def _generate_markdown_report(self):
        """Generate markdown report for documentation"""
        
        md_content = [
            "# M1K3 Refactoring Checklist",
            "",
            f"*Generated: {time.strftime('%Y-%m-%d %H:%M:%S')}*",
            "",
            "## Summary",
            "",
            f"**Total Tasks:** {len(self.tasks)}",
            ""
        ]
        
        # Priority breakdown
        priority_counts = {}
        category_counts = {}
        effort_counts = {}
        
        for task in self.tasks:
            priority_counts[task.priority] = priority_counts.get(task.priority, 0) + 1
            category_counts[task.category] = category_counts.get(task.category, 0) + 1  
            effort_counts[task.effort_estimate] = effort_counts.get(task.effort_estimate, 0) + 1
        
        md_content.extend([
            "### By Priority",
            ""
        ])
        
        for priority in ["Critical", "High", "Medium", "Low"]:
            count = priority_counts.get(priority, 0)
            if count > 0:
                md_content.append(f"- **{priority}:** {count} tasks")
        
        md_content.extend([
            "",
            "### By Category", 
            ""
        ])
        
        for category, count in sorted(category_counts.items()):
            md_content.append(f"- **{category}:** {count} tasks")
        
        md_content.extend([
            "",
            "### By Effort Estimate",
            ""
        ])
        
        for effort, count in sorted(effort_counts.items()):
            md_content.append(f"- **{effort}:** {count} tasks")
        
        # Detailed task list
        md_content.extend([
            "",
            "## Detailed Task List",
            ""
        ])
        
        current_priority = None
        for i, task in enumerate(self.tasks, 1):
            if task.priority != current_priority:
                current_priority = task.priority
                md_content.extend([
                    f"### {task.priority} Priority",
                    ""
                ])
            
            md_content.extend([
                f"#### {i}. {task.title}",
                "",
                f"**Category:** {task.category}  ",
                f"**Effort:** {task.effort_estimate}  ",
                "",
                f"{task.description}",
                "",
                "**Files Involved:**",
                ""
            ])
            
            for file_path in task.files_involved:
                md_content.append(f"- `{file_path}`")
            
            if task.blockers:
                md_content.extend([
                    "",
                    "**Blockers:**",
                    ""
                ])
                for blocker in task.blockers:
                    md_content.append(f"- {blocker}")
            
            md_content.extend([
                "",
                "**Benefits:**",
                ""
            ])
            for benefit in task.benefits:
                md_content.append(f"- {benefit}")
            
            md_content.extend(["", "---", ""])
        
        # Performance context if available
        if self.benchmark_results:
            summary = self.benchmark_results.get('summary', {})
            md_content.extend([
                "## Performance Context",
                "",
                f"- **Legacy Startup Time:** {summary.get('legacy_startup_time', 0):.2f}s",
                f"- **Optimized Startup Time:** {summary.get('optimized_startup_time', 0):.2f}s", 
                f"- **Critical Ready Time:** {summary.get('critical_ready_time', 0):.2f}s",
                f"- **Overall Improvement:** {summary.get('overall_improvement_percent', 0):.1f}%",
                f"- **Target:** 50%+ improvement for production readiness",
                ""
            ])
        
        # Save markdown report
        md_file = Path(__file__).parent / "refactoring_checklist.md"
        with open(md_file, 'w') as f:
            f.write('\n'.join(md_content))


def main():
    """Generate refactoring checklist"""
    try:
        analyzer = RefactoringAnalyzer()
        analyzer.generate_report()
        return True
    except KeyboardInterrupt:
        print("\n⚠️ Checklist generation interrupted by user")
        return False
    except Exception as e:
        print(f"\n💥 Checklist generation failed: {e}")
        return False


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)