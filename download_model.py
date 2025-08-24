#!/usr/bin/env python3
"""
Model Downloader for M1K3 using Ollama
"""

import time
import psutil
import argparse
import subprocess
import shutil
import json
import os
import re
import logging

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

def check_ollama():
    """Check if Ollama is installed."""
    logging.info("Checking for Ollama installation.")
    if not shutil.which("ollama"):
        logging.error("Ollama is not installed or not in the system's PATH.")
        print("❌ Ollama is not installed or not in the system's PATH.")
        print("🔧 Please install Ollama from https://ollama.com and try again.")
        return False
    print("✅ Ollama is installed.")
    logging.info("Ollama is installed.")
    return True

def check_system_requirements():
    """Check if system can handle the model"""
    print("🔍 System Requirements Check")
    print("-" * 40)
    
    # Check RAM
    ram_gb = psutil.virtual_memory().total / (1024**3)
    available_ram_gb = psutil.virtual_memory().available / (1024**3)
    
    print(f"💾 Total RAM: {ram_gb:.1f}GB")
    print(f"💾 Available RAM: {available_ram_gb:.1f}GB")
    print(f"💾 Required RAM: ~4GB")
    
    if available_ram_gb < 4:
        print("⚠️  Warning: Low available RAM. Model may run slowly.")
        logging.warning(f"Low available RAM ({available_ram_gb:.1f}GB). Model may run slowly.")
    else:
        print("✅ Sufficient RAM for optimal performance")
        logging.info(f"Sufficient RAM ({available_ram_gb:.1f}GB) for optimal performance.")
    
    # Check disk space
    disk_space = psutil.disk_usage('/').free / (1024**3)
    print(f"💽 Available disk space: {disk_space:.1f}GB")
    print(f"💽 Required space: ~4GB for model files")
    
    if disk_space < 4:
        print("❌ Insufficient disk space")
        logging.error(f"Insufficient disk space ({disk_space:.1f}GB). Required ~4GB.")
        return False
    else:
        print("✅ Sufficient disk space")
        logging.info(f"Sufficient disk space ({disk_space:.1f}GB) for model files.")
    
    print()
    logging.info("System requirements check passed.")
    return True

def parse_ollama_show(output):
    """Parses the plain-text output of 'ollama show'."""
    logging.info("Parsing 'ollama show' output.")
    logging.debug(f"Raw output:\n{output}")
    metadata = {}
    lines = output.strip().splitlines()
    if not lines:
        logging.warning("No lines in output to parse.")
        return metadata

    def get_indent(line):
        return len(line) - len(line.lstrip(' '))

    try:
        base_indent = get_indent(next(line for line in lines if line.strip()))
        # Debug: Base indent determined as {base_indent}
    except StopIteration:
        logging.warning("Could not determine base indent from lines.")
        return metadata

    sections = {}
    current_section = None
    
    # Enhanced parsing for ollama format: 2-space headers, 4-space content
    section_indent = 2  # Ollama uses 2 spaces for section headers
    content_indent = 4  # Ollama uses 4 spaces for content
    
    for line_num, line in enumerate(lines):
        if not line.strip():
            continue
        indent = get_indent(line)
        
        # Detect new sections - look for 2-space indent
        if indent == section_indent:
            section_name = line.strip().lower()
            # Handle common ollama section headers
            if section_name.endswith(':'):
                section_name = section_name[:-1]
            current_section = section_name
            sections[current_section] = []
            # Debug: Found section '{current_section}' at indent {indent}
        elif indent == content_indent and current_section:
            sections[current_section].append(line.strip())
            # Debug: Added content to '{current_section}': {line.strip()[:50]}...
        elif indent == 0 and line.strip() and current_section is None:
            # This is the first section without proper header (e.g., "Model" at start)
            if line.strip().lower() == 'model':
                current_section = 'model'
                sections[current_section] = []
            else:
                # Handle other content before first section header
                if 'general' not in sections:
                    sections['general'] = []
                sections['general'].append(line.strip())
        elif current_section is None and line.strip():
            # Handle content before first section header
            if 'general' not in sections:
                sections['general'] = []
            sections['general'].append(line.strip())

    # Enhanced content processing
    for section, content in sections.items():
        if not content:
            metadata[section] = ""
            continue
        
        # Special handling for different section types
        if section in ['system', 'template']:
            # Preserve full text for system prompts and templates
            # Join content and clean it up
            system_text = '\n'.join(content).strip()
            if system_text:
                metadata[section] = system_text
            else:
                metadata[section] = '\n'.join(content)
        elif section == 'parameters':
            # Parse parameters as key-value pairs
            temp_data = {}
            for line in content:
                if ':' in line:
                    key, value = line.split(':', 1)
                    key = key.strip().lower()
                    value = value.strip()
                    # Handle special parameter types
                    if key == 'stop':
                        # Parse stop sequences
                        if value.startswith('[') and value.endswith(']'):
                            try:
                                import ast
                                temp_data[key] = ast.literal_eval(value)
                            except:
                                temp_data[key] = [v.strip('"\' ') for v in value[1:-1].split(',') if v.strip()]
                        else:
                            temp_data[key] = [value.strip('"\' ')]
                    else:
                        temp_data[key] = value
                elif '=' in line:
                    key, value = line.split('=', 1)
                    temp_data[key.strip().lower()] = value.strip()
                else:
                    # Single value parameters
                    temp_data[line.strip().lower()] = True
            metadata[section] = temp_data if temp_data else '\n'.join(content)
        elif section == 'model':
            # Parse model information as key-value pairs with multiple spaces as separator
            temp_data = {}
            for line in content:
                # Split on multiple spaces (2 or more)
                parts = re.split(r'\s{2,}', line.strip())
                if len(parts) >= 2:
                    key = parts[0].strip().lower().replace(' ', '_')
                    value = parts[1].strip()
                    temp_data[key] = value
                elif line.strip():
                    # Single value or description
                    temp_data[line.strip().lower().replace(' ', '_')] = True
            metadata[section] = temp_data if temp_data else '\n'.join(content)
        else:
            # Try key-value parsing for other sections
            is_kv_section = True
            temp_data = {}
            for line in content:
                # Try multiple spaces first, then other separators
                found_separator = False
                
                # Try multiple spaces (most common in ollama output)
                parts = re.split(r'\s{2,}', line.strip())
                if len(parts) >= 2:
                    key = parts[0].strip().lower().replace(' ', '_')
                    value = parts[1].strip()
                    if key in temp_data:
                        if not isinstance(temp_data[key], list):
                            temp_data[key] = [temp_data[key]]
                        temp_data[key].append(value)
                    else:
                        temp_data[key] = value
                    found_separator = True
                else:
                    # Try other separators
                    for sep in [':', '=', '\t']:
                        if sep in line:
                            split_parts = line.split(sep, 1)
                            if len(split_parts) == 2:
                                key = split_parts[0].strip().lower().replace(' ', '_')
                                value = split_parts[1].strip()
                                if key in temp_data:
                                    if not isinstance(temp_data[key], list):
                                        temp_data[key] = [temp_data[key]]
                                    temp_data[key].append(value)
                                else:
                                    temp_data[key] = value
                                found_separator = True
                                break
                
                if not found_separator:
                    is_kv_section = False
                    break
            
            if is_kv_section and temp_data:
                metadata[section] = temp_data
            elif all(len(line.split()) == 1 for line in content):
                # Single word lines - make a list
                metadata[section] = [line.strip() for line in content]
            else:
                # Full text content
                metadata[section] = '\n'.join(content)
            
    logging.info(f"Successfully parsed metadata for {len(metadata)} sections.")
    logging.debug(f"Parsed metadata: {json.dumps(metadata, indent=2, default=str)}")
    return metadata

def format_metadata(raw_metadata, model_name):
    """Formats the raw parsed metadata into a system-friendly structure."""
    logging.info(f"Formatting metadata for model '{model_name}'.")
    formatted = {
        "name": model_name,
        "model_info": {},
        "parameters": {},
        "prompts": {},
        "license": raw_metadata.get('license', 'N/A'),
        "raw_metadata": raw_metadata  # Preserve original for debugging
    }

    # Enhanced model info extraction
    model_data = raw_metadata.get('model', {})
    if isinstance(model_data, dict):
        # Map various possible field names
        field_mappings = {
            'architecture': ['architecture', 'arch', 'model_type'],
            'parameter_count': ['parameters', 'params', 'parameter_count', 'param_count'],
            'quantization': ['quantization', 'quant', 'quantization_type'],
            'context_length': ['context length', 'context_length', 'max_position_embeddings', 'max_context'],
            'embedding_length': ['embedding length', 'embedding_length', 'hidden_size'],
            'family': ['family', 'model_family'],
            'format': ['format', 'model_format']
        }
        
        for target_field, possible_keys in field_mappings.items():
            value = None
            for key in possible_keys:
                if key in model_data:
                    value = model_data[key]
                    break
            
            if value:
                if target_field in ['context_length', 'embedding_length', 'parameter_count']:
                    try:
                        # Try to extract numeric values
                        if isinstance(value, str):
                            # Extract numbers from strings like "2048" or "2.7B"
                            import re
                            numbers = re.findall(r'([0-9.]+)([KMGTB]?)', value.upper())
                            if numbers:
                                num, unit = numbers[0]
                                num = float(num)
                                if unit:
                                    multipliers = {'K': 1000, 'M': 1000000, 'B': 1000000000, 'T': 1000000000000}
                                    num *= multipliers.get(unit, 1)
                                value = int(num)
                        formatted['model_info'][target_field] = value
                    except (ValueError, TypeError):
                        formatted['model_info'][target_field] = str(value)
                else:
                    formatted['model_info'][target_field] = value
    
    # Add any other model data that wasn't mapped
    if isinstance(model_data, dict):
        for key, value in model_data.items():
            if key.lower() not in ['architecture', 'parameters', 'params', 'quantization', 
                                 'context length', 'context_length', 'embedding length', 'embedding_length']:
                formatted['model_info'][f'extra_{key.lower()}'] = value
    
    formatted['model_info']['capabilities'] = raw_metadata.get('capabilities', [])

    # Enhanced parameter extraction
    param_data = raw_metadata.get('parameters', {})
    if isinstance(param_data, dict):
        # Preserve all parameters
        for key, value in param_data.items():
            formatted['parameters'][key] = value
        
        # Special handling for stop sequences
        stop_sequences = param_data.get('stop', [])
        if stop_sequences:
            if not isinstance(stop_sequences, list):
                stop_sequences = [stop_sequences]
            formatted['parameters']['stop_sequences'] = [str(s).strip('"\' ') for s in stop_sequences]
    
    # Enhanced prompt extraction
    system_prompt = raw_metadata.get('system', raw_metadata.get('system_prompt', ''))
    template = raw_metadata.get('template', raw_metadata.get('chat_template', ''))
    
    # Clean up system prompts
    if system_prompt and system_prompt != 'N/A':
        # Remove common prefixes/suffixes that might be artifacts
        system_prompt = system_prompt.strip()
        if system_prompt.startswith('System:'):
            system_prompt = system_prompt[7:].strip()
    
    formatted['prompts']['system'] = system_prompt if system_prompt and system_prompt != 'N/A' else None
    formatted['prompts']['template'] = template if template and template != 'N/A' else None
    
    # Add template format detection
    if template:
        template_lower = template.lower()
        if '<|im_start|>' in template_lower:
            formatted['prompts']['template_type'] = 'chatml'
        elif '[inst]' in template_lower:
            formatted['prompts']['template_type'] = 'llama2'
        elif '### instruction' in template_lower:
            formatted['prompts']['template_type'] = 'alpaca'
        elif 'user:' in template_lower and 'assistant:' in template_lower:
            formatted['prompts']['template_type'] = 'vicuna'
        else:
            formatted['prompts']['template_type'] = 'custom'

    # Add extraction timestamp and ollama version info
    formatted['metadata'] = {
        'extracted_at': time.time(),
        'ollama_version': raw_metadata.get('ollama_version', 'unknown')
    }

    logging.info("Successfully formatted metadata.")
    logging.debug(f"Formatted metadata: {json.dumps(formatted, indent=2, default=str)}")
    return formatted

def get_model_metadata(model_name, save_dir='models'):
    """Get model metadata from Ollama and save to a file."""
    logging.info(f"Fetching metadata for '{model_name}'.")
    print(f"🔍 Fetching metadata for '{model_name}'...")

    sanitized_model_name = model_name.replace("/", "_").replace(":", "_")
    file_path = os.path.join(save_dir, f"{sanitized_model_name}.json")

    # Remove old metadata file if it exists to ensure freshness
    if os.path.exists(file_path):
        logging.info(f"Removing existing metadata file: {file_path}")
        os.remove(file_path)

    try:
        metadata = {}
        # First, try with --json for newer ollama versions
        logging.info("Attempting to fetch metadata with --json flag.")
        result = subprocess.run(
            ["ollama", "show", "--json", model_name],
            capture_output=True, text=True, check=False
        )

        if result.returncode == 0:
            logging.info("Successfully fetched metadata using --json flag.")
            metadata = json.loads(result.stdout)
        else:
            # Fallback for older ollama versions or other errors
            logging.info("--json flag not supported or failed. Falling back to plain text parsing.")
            result = subprocess.run(
                ["ollama", "show", model_name],
                capture_output=True, text=True, check=True
            )
            metadata = parse_ollama_show(result.stdout)
        
        if not metadata:
            logging.warning("Parsed metadata is empty. Skipping file write.")
            print("❌ Metadata is empty. Skipping file write.")
            return None
        
        formatted_metadata = format_metadata(metadata, model_name)

        if not os.path.exists(save_dir):
            os.makedirs(save_dir)
            logging.info(f"Created directory: {save_dir}")
            print(f"📁 Created directory: {save_dir}")

        with open(file_path, 'w') as f:
            json.dump(formatted_metadata, f, indent=4)
        
        logging.info(f"Metadata saved to '{file_path}'.")
        print(f"✅ Metadata saved to '{file_path}'")

        print("\n" + "-" * 25 + " Model Metadata " + "-" * 25)
        model_info = formatted_metadata.get('model_info', {})
        parameters = formatted_metadata.get('parameters', {})
        prompts = formatted_metadata.get('prompts', {})

        print(f"📋 Model: {formatted_metadata.get('name', 'N/A')}")
        print(f"🏗️  Architecture: {model_info.get('architecture', 'N/A')}")
        print(f"📊 Parameter Count: {model_info.get('parameter_count', 'N/A')}")
        print(f"🔧 Quantization: {model_info.get('quantization', 'N/A')}")
        print(f"📏 Context Length: {model_info.get('context_length', 'N/A')}")
        
        # Display system prompt with truncation for readability
        system_prompt = prompts.get('system')
        if system_prompt and system_prompt.strip():
            if len(system_prompt) > 200:
                print(f"🤖 System Prompt: {system_prompt[:200]}...")
                print(f"   [Full system prompt saved to metadata file]")
            else:
                print(f"🤖 System Prompt: {system_prompt}")
        else:
            print(f"🤖 System Prompt: [None specified]")
            
        template_type = prompts.get('template_type', 'unknown')
        print(f"📝 Chat Template: {template_type}")
        
        stop_seqs = parameters.get('stop_sequences', [])
        if stop_seqs:
            print(f"🛑 Stop Sequences: {', '.join(stop_seqs[:3])}{'...' if len(stop_seqs) > 3 else ''}")
        else:
            print(f"🛑 Stop Sequences: [Default]")
            
        # Show additional parameters if present
        other_params = {k: v for k, v in parameters.items() if k not in ['stop_sequences', 'stop']}
        if other_params:
            print(f"⚙️  Parameters: {len(other_params)} custom settings stored")
            
        print("-" * 66 + "\n")

        return formatted_metadata
    except subprocess.CalledProcessError as e:
        error_msg = e.stderr if e.stderr else "Unknown subprocess error"
        logging.error(f"Failed to get metadata for '{model_name}'. Subprocess error: {error_msg}")
        print(f"\n❌ Failed to get metadata for '{model_name}': {error_msg}")
        print(f"💡 Make sure the model is pulled with: ollama pull {model_name}")
        return None
    except json.JSONDecodeError as e:
        logging.error(f"Failed to parse JSON metadata for '{model_name}': {e}")
        print(f"\n❌ Invalid JSON response from ollama show --json")
        print(f"💡 This might be an older ollama version or corrupted model")
        return None
    except Exception as e:
        logging.error(f"An unexpected error occurred while fetching metadata for '{model_name}'.", exc_info=True)
        print(f"\n❌ An error occurred while fetching metadata: {e}")
        print(f"💡 Try running: ollama list to verify the model exists")
        return None

def download_model(model_name):
    """Download a model using Ollama."""
    print(f"🚀 M1K3 Ollama Model Downloader")
    print("=" * 50)
    print(f"📦 Model: {model_name}")
    print()
    
    if not check_ollama():
        return False
    
    if not check_system_requirements():
        print("❌ System requirements not met")
        return False
    
    print(f"🚚 Pulling model '{model_name}' via Ollama...")
    print("⏳ This may take some time depending on connection speed...")
    print("-" * 50)
    
    start_time = time.time()
    try:
        process = subprocess.Popen(
            ["ollama", "pull", model_name],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            universal_newlines=True,
        )

        for line in process.stdout:
            print(line, end='')

        process.wait()

        if process.returncode != 0:
            logging.error(f"Ollama pull failed for model '{model_name}' with exit code {process.returncode}.")
            print(f"\n❌ Ollama pull failed with exit code {process.returncode}")
            return False

        download_time = time.time() - start_time
        logging.info(f"Successfully pulled model '{model_name}' in {download_time:.1f} seconds.")
        print("-" * 50)
        print("✅ Model pulled successfully!")
        print(f"⏱️  Total time: {download_time:.1f}s ({download_time/60:.1f} minutes)")
        print()
        
        get_model_metadata(model_name)
        
        print("📝 Next Steps:")
        print(f"   1. The model '{model_name}' is now available in Ollama.")
        print(f"   2. You can run it with: ollama run {model_name}")
        print("   3. Configure M1K3 to use Ollama as the backend.")
        print()
        
        return True
        
    except FileNotFoundError:
        logging.error("'ollama' command not found. Make sure Ollama is installed and in your PATH.")
        print("\n❌ 'ollama' command not found. Make sure Ollama is installed and in your PATH.")
        return False
    except KeyboardInterrupt:
        logging.warning("Download interrupted by user.")
        print("\n⚠️  Download interrupted by user")
        return False
    except Exception as e:
        logging.error(f"An unexpected error occurred during download of model '{model_name}'.", exc_info=True)
        print(f"\n❌ An error occurred: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="M1K3 Model Downloader using Ollama")
    parser.add_argument(
        'model',
        nargs='?',
        default='smollm2:135m',
        help='The model to pull from Ollama (e.g., "llama3")'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Enable detailed logging output.'
    )
    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    logging.info("Starting M1K3 Model Downloader.")
    print("🤖 M1K3 Model Downloader")
    print()
    
    success = download_model(model_name=args.model)
    
    if success:
        logging.info(f"Successfully processed model '{args.model}'.")
        print(f"✨ {args.model} is ready to use with Ollama!")
    else:
        logging.error(f"Failed to process model '{args.model}'.")
        print("❌ Installation failed. Check the error messages above.")
        return 1
    
    logging.info("M1K3 Model Downloader finished.")
    return 0

if __name__ == "__main__":
    exit(main())