.PHONY: install download test test-voice demo run run-silent benchmark clean help

help:
	@echo "M1K3 Local AI CLI with Voice - Available Commands:"
	@echo ""
	@echo "  install     - Install Python dependencies"
	@echo "  download    - Download SmolLM-135M model" 
	@echo "  test        - Run quick functionality test"
	@echo "  test-voice  - Test voice synthesis"
	@echo "  demo        - Run voice & greeting demonstration"
	@echo "  run         - Start interactive CLI with voice"
	@echo "  run-silent  - Start interactive CLI without voice"
	@echo "  benchmark   - Run performance benchmarks"
	@echo "  clean       - Remove downloaded models"
	@echo "  help        - Show this help message"
	@echo ""

install:
	@echo "📦 Installing dependencies..."
	pip install -r requirements.txt

download:
	@echo "⬇️  Downloading SmolLM-135M model..."
	python download_model.py

test:
	@echo "🧪 Running functionality test..."
	python cli.py --no-voice --query "Hello M1K3!"

test-voice:
	@echo "🔊 Testing voice synthesis..."
	python cli.py --test-voice

demo:
	@echo "🎮 Running M1K3 PlayStation 1 voice demo..."
	python retro_demo.py

run:
	@echo "🎮 Starting M1K3 with PlayStation 1 retro voice..."
	python m1k3.py

run-silent:
	@echo "🚀 Starting M1K3 CLI without voice..."
	python cli.py --no-voice

benchmark:
	@echo "📊 Running performance benchmarks..."
	python benchmark.py

clean:
	@echo "🧹 Cleaning up models..."
	rm -rf models/

# Quick setup for new users
setup: install download
	@echo "✅ M1K3 setup complete! Run 'make run' to start."