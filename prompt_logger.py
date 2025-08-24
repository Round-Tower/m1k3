#!/usr/bin/env python3
"""
Prompt Logger for M1K3
Comprehensive logging of all prompts, templates, and model I/O
"""

import json
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any
import textwrap

class PromptLogger:
    """Enhanced logging for model prompts and responses"""
    
    def __init__(self, log_dir: str = "logs", enable_console: bool = True, enable_file: bool = True):
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(exist_ok=True)
        self.enable_console = enable_console
        self.enable_file = enable_file
        
        # Create session log file
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.session_file = self.log_dir / f"prompt_session_{timestamp}.log"
        self.json_file = self.log_dir / f"prompt_session_{timestamp}.json"
        
        self.session_data = []
        self.current_exchange = None
        
        if self.enable_console:
            print(f"📝 Prompt logging enabled")
            print(f"📁 Log file: {self.session_file}")
    
    def log_model_info(self, model_name: str, backend: str, metadata: Optional[Dict] = None):
        """Log model information at startup"""
        info = {
            "timestamp": time.time(),
            "event": "model_info",
            "model_name": model_name,
            "backend": backend,
            "metadata": metadata or {}
        }
        
        self._write_to_console("🤖 MODEL INFORMATION", "=")
        self._write_to_console(f"Model: {model_name}")
        self._write_to_console(f"Backend: {backend}")
        
        if metadata:
            if "model_info" in metadata:
                model_info = metadata["model_info"]
                self._write_to_console(f"Architecture: {model_info.get('architecture', 'Unknown')}")
                self._write_to_console(f"Parameters: {model_info.get('parameter_count', 'Unknown')}")
                self._write_to_console(f"Context: {model_info.get('context_length', 'Unknown')}")
            
            if "prompts" in metadata:
                prompts = metadata["prompts"]
                if prompts.get("system"):
                    self._write_to_console(f"System Prompt: {prompts['system'][:100]}...")
                if prompts.get("template_type"):
                    self._write_to_console(f"Template Type: {prompts['template_type']}")
        
        self._write_json(info)
    
    def log_prompt_format(self, template_name: str, template_format: str, variables: Dict[str, Any]):
        """Log template formatting details"""
        info = {
            "timestamp": time.time(),
            "event": "template_format",
            "template_name": template_name,
            "template_format": template_format,
            "variables": variables
        }
        
        self._write_to_console("📋 TEMPLATE FORMATTING", "-")
        self._write_to_console(f"Template: {template_name}")
        self._write_to_console(f"Format: {template_format}")
        
        if variables:
            self._write_to_console("Variables:")
            for key, value in variables.items():
                if isinstance(value, str) and len(value) > 100:
                    self._write_to_console(f"  {key}: {value[:100]}...")
                else:
                    self._write_to_console(f"  {key}: {value}")
        
        self._write_json(info)
    
    def log_raw_prompt(self, prompt: str, context_messages: Optional[List[Dict]] = None):
        """Log the raw user prompt before processing"""
        self.current_exchange = {
            "timestamp": time.time(),
            "raw_prompt": prompt,
            "context_messages": context_messages or []
        }
        
        self._write_to_console("💬 RAW USER PROMPT", "=")
        self._write_to_console(prompt)
        
        if context_messages:
            self._write_to_console(f"\n📚 Context: {len(context_messages)} messages")
            for i, msg in enumerate(context_messages[-3:]):  # Show last 3
                role = msg.get("role", "unknown")
                content = msg.get("content", "")[:50]
                self._write_to_console(f"  [{i}] {role}: {content}...")
    
    def log_formatted_prompt(self, formatted_prompt: str, format_type: str = "unknown"):
        """Log the formatted prompt sent to model"""
        if self.current_exchange:
            self.current_exchange["formatted_prompt"] = formatted_prompt
            self.current_exchange["format_type"] = format_type
        
        self._write_to_console("\n🎨 FORMATTED PROMPT", "-")
        self._write_to_console(f"Format Type: {format_type}")
        
        # Show formatted prompt with clear boundaries
        self._write_to_console("┌─ START FORMATTED PROMPT ─┐")
        
        # Truncate very long prompts for console display
        display_prompt = formatted_prompt
        if len(formatted_prompt) > 500:
            display_prompt = formatted_prompt[:250] + "\n...[truncated]...\n" + formatted_prompt[-250:]
        
        for line in display_prompt.split('\n'):
            self._write_to_console(f"│ {line}")
        
        self._write_to_console("└─ END FORMATTED PROMPT ─┘")
        
        # Show token/character counts
        self._write_to_console(f"\n📊 Stats: {len(formatted_prompt)} chars, ~{len(formatted_prompt)//4} tokens")
    
    def log_model_parameters(self, params: Dict[str, Any]):
        """Log generation parameters"""
        if self.current_exchange:
            self.current_exchange["model_parameters"] = params
        
        self._write_to_console("\n⚙️  GENERATION PARAMETERS", "-")
        for key, value in params.items():
            self._write_to_console(f"  {key}: {value}")
    
    def log_raw_response(self, response: str, generation_time: Optional[float] = None):
        """Log the raw model response"""
        if self.current_exchange:
            self.current_exchange["raw_response"] = response
            self.current_exchange["generation_time"] = generation_time
        
        self._write_to_console("\n🤖 RAW MODEL RESPONSE", "=")
        
        # Truncate very long responses for console
        display_response = response
        if len(response) > 500:
            display_response = response[:250] + "\n...[truncated]...\n" + response[-250:]
        
        self._write_to_console(display_response)
        
        if generation_time:
            tokens = len(response.split())
            tokens_per_sec = tokens / generation_time if generation_time > 0 else 0
            self._write_to_console(f"\n⏱️  Generated in {generation_time:.2f}s ({tokens_per_sec:.1f} tokens/sec)")
    
    def log_processed_response(self, processed_response: str, processing_notes: Optional[str] = None):
        """Log the processed/cleaned response"""
        if self.current_exchange:
            self.current_exchange["processed_response"] = processed_response
            self.current_exchange["processing_notes"] = processing_notes
            
            # Save complete exchange
            self.session_data.append(self.current_exchange)
            self._write_json(self.current_exchange)
        
        if processed_response != self.current_exchange.get("raw_response"):
            self._write_to_console("\n✨ PROCESSED RESPONSE", "-")
            self._write_to_console(processed_response[:500])
            
            if processing_notes:
                self._write_to_console(f"\n📝 Processing: {processing_notes}")
        
        self._write_to_console("\n" + "="*60 + "\n")
    
    def log_error(self, error: str, context: Optional[Dict] = None):
        """Log errors in prompt processing"""
        error_data = {
            "timestamp": time.time(),
            "event": "error",
            "error": error,
            "context": context or {}
        }
        
        self._write_to_console("\n❌ ERROR", "!")
        self._write_to_console(error)
        
        if context:
            self._write_to_console("Context:")
            for key, value in context.items():
                self._write_to_console(f"  {key}: {value}")
        
        self._write_json(error_data)
    
    def log_metadata_search(self, search_path: str, found: bool, content: Optional[Dict] = None):
        """Log metadata file searches"""
        self._write_to_console("\n🔍 METADATA SEARCH", "-")
        self._write_to_console(f"Path: {search_path}")
        self._write_to_console(f"Found: {'✅' if found else '❌'}")
        
        if found and content:
            self._write_to_console("Content preview:")
            preview = json.dumps(content, indent=2)[:200]
            self._write_to_console(preview)
    
    def _write_to_console(self, message: str, separator: Optional[str] = None):
        """Write to console with formatting"""
        if not self.enable_console:
            return
        
        if separator:
            if separator == "=":
                print("\n" + "="*60)
                print(message)
                print("="*60)
            elif separator == "-":
                print("\n" + "-"*40)
                print(message)
                print("-"*40)
            elif separator == "!":
                print("\n" + "!"*60)
                print(message)
                print("!"*60)
        else:
            print(message)
    
    def _write_to_file(self, message: str):
        """Write to log file"""
        if not self.enable_file:
            return
        
        with open(self.session_file, 'a', encoding='utf-8') as f:
            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            f.write(f"[{timestamp}] {message}\n")
    
    def _write_json(self, data: Dict):
        """Write to JSON log file"""
        if not self.enable_file:
            return
        
        with open(self.json_file, 'a', encoding='utf-8') as f:
            f.write(json.dumps(data) + "\n")
    
    def get_session_summary(self) -> Dict:
        """Get summary of the logging session"""
        return {
            "total_exchanges": len(self.session_data),
            "total_tokens": sum(len(e.get("raw_response", "").split()) for e in self.session_data),
            "average_generation_time": sum(e.get("generation_time", 0) for e in self.session_data) / max(len(self.session_data), 1),
            "session_file": str(self.session_file),
            "json_file": str(self.json_file)
        }


# Global logger instance
_prompt_logger = None

def get_prompt_logger() -> PromptLogger:
    """Get or create the global prompt logger"""
    global _prompt_logger
    if _prompt_logger is None:
        _prompt_logger = PromptLogger()
    return _prompt_logger

def enable_prompt_logging(console: bool = True, file: bool = True):
    """Enable prompt logging"""
    global _prompt_logger
    _prompt_logger = PromptLogger(enable_console=console, enable_file=file)
    return _prompt_logger

def disable_prompt_logging():
    """Disable prompt logging"""
    global _prompt_logger
    if _prompt_logger:
        _prompt_logger.enable_console = False
        _prompt_logger.enable_file = False