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
    except StopIteration:
        logging.warning("Could not determine base indent from lines.")
        return metadata

    sections = {}
    current_section = None
    
    for line in lines:
        if not line.strip():
            continue
        indent = get_indent(line)
        if indent == base_indent:
            current_section = line.strip().lower()
            sections[current_section] = []
        elif indent > base_indent and current_section:
            sections[current_section].append(line.strip())

    for section, content in sections.items():
        if not content:
            metadata[section] = ""
            continue
        
        is_kv_section = True
        temp_data = {}
        for line in content:
            parts = re.split(r'\s{2,}', line.strip(), 1)
            if len(parts) == 2:
                key, value = parts[0].strip().lower(), parts[1].strip()
                if key in temp_data:
                    if not isinstance(temp_data[key], list):
                        temp_data[key] = [temp_data[key]]
                    temp_data[key].append(value)
                else:
                    temp_data[key] = value
            else:
                is_kv_section = False
                break
        
        if is_kv_section:
            metadata[section] = temp_data
            continue

        if all(len(line.split()) == 1 for line in content):
             metadata[section] = [line.strip() for line in content]
             continue

        metadata[section] = '\n'.join(content)
            
    logging.info(f"Successfully parsed metadata for {len(metadata)} sections.")
    logging.debug(f"Parsed metadata: {json.dumps(metadata, indent=2)}")
    return metadata

def format_metadata(raw_metadata, model_name):
    """Formats the raw parsed metadata into a system-friendly structure."""
    logging.info(f"Formatting metadata for model '{model_name}'.")
    formatted = {
        "name": model_name,
        "model_info": {},
        "parameters": {},
        "prompts": {},
        "license": raw_metadata.get('license', 'N/A')
    }

    model_data = raw_metadata.get('model', {})
    if isinstance(model_data, dict):
        formatted['model_info']['architecture'] = model_data.get('architecture')
        formatted['model_info']['parameter_count'] = model_data.get('parameters')
        formatted['model_info']['quantization'] = model_data.get('quantization')
        try:
            formatted['model_info']['context_length'] = int(model_data.get('context length'))
        except (ValueError, TypeError):
            formatted['model_info']['context_length'] = None
        try:
            formatted['model_info']['embedding_length'] = int(model_data.get('embedding length'))
        except (ValueError, TypeError):
            formatted['model_info']['embedding_length'] = None
    
    formatted['model_info']['capabilities'] = raw_metadata.get('capabilities', [])

    param_data = raw_metadata.get('parameters', {})
    if isinstance(param_data, dict):
        stop_sequences = param_data.get('stop', [])
        if stop_sequences and not isinstance(stop_sequences, list):
            stop_sequences = [stop_sequences]
        formatted['parameters']['stop_sequences'] = [s.strip('"') for s in stop_sequences]

    formatted['prompts']['system'] = raw_metadata.get('system', 'N/A')
    formatted['prompts']['template'] = raw_metadata.get('template', 'N/A')

    logging.info("Successfully formatted metadata.")
    logging.debug(f"Formatted metadata: {json.dumps(formatted, indent=2)}")
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

        print("\n" + "-" * 20 + " Model Metadata " + "-" * 20)
        model_info = formatted_metadata.get('model_info', {})
        parameters = formatted_metadata.get('parameters', {})
        prompts = formatted_metadata.get('prompts', {})

        print(f"Architecture: {model_info.get('architecture', 'N/A')}")
        print(f"Parameter Count: {model_info.get('parameter_count', 'N/A')}")
        print(f"System Prompt: {prompts.get('system', 'N/A')}")
        print(f"Stop Sequences: {parameters.get('stop_sequences', 'N/A')}")
        print("-" * 58 + "\n")

        return formatted_metadata
    except subprocess.CalledProcessError as e:
        logging.error(f"Failed to get metadata for '{model_name}'. Subprocess error: {e.stderr}")
        print(f"\n❌ Failed to get metadata for '{model_name}': {e.stderr}")
        return None
    except Exception as e:
        logging.error(f"An unexpected error occurred while fetching metadata for '{model_name}'.", exc_info=True)
        print(f"\n❌ An error occurred while fetching metadata: {e}")
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
        default='dagbs/qwen3-coder:flash',
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