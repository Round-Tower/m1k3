

import pytest
import requests
import subprocess
import time
import os
from multiprocessing import Process
import uvicorn
import tempfile
import sys

# Import the app from the server file
from mcp_server import app

SERVER_HOST = "127.0.0.1"
SERVER_PORT = 8001
BASE_URL = f"http://{SERVER_HOST}:{SERVER_PORT}"

def run_server(stdout_log, stderr_log):
    """Function to run in a separate process, with logging."""
    with open(stdout_log, 'w') as f_out, open(stderr_log, 'w') as f_err:
        # Redirect stdout and stderr to log files
        sys.stdout = f_out
        sys.stderr = f_err
        uvicorn.run(app, host=SERVER_HOST, port=SERVER_PORT, log_level="info")

@pytest.fixture(scope="module")
def mcp_server():
    """
    Pytest fixture to start and stop the MCP server in a background process.
    Captures server logs for debugging.
    """
    stdout_log = tempfile.NamedTemporaryFile(delete=False, suffix="_stdout.log")
    stderr_log = tempfile.NamedTemporaryFile(delete=False, suffix="_stderr.log")
    
    server_process = Process(target=run_server, args=(stdout_log.name, stderr_log.name))
    server_process.start()
    
    # Wait for the server to be ready (increased timeout)
    retries = 15
    server_ready = False
    while retries > 0:
        try:
            response = requests.get(f"{BASE_URL}/")
            if response.status_code == 200:
                server_ready = True
                break
        except requests.ConnectionError:
            time.sleep(1)
            retries -= 1
    
    if not server_ready:
        server_process.terminate()
        with open(stdout_log.name, 'r') as f:
            stdout_content = f.read()
        with open(stderr_log.name, 'r') as f:
            stderr_content = f.read()
        
        # Print logs to stdout for capturing
        print("--- SERVER STDOUT ---")
        print(stdout_content)
        print("--- SERVER STDERR ---")
        print(stderr_content)
        print("---------------------")

        os.unlink(stdout_log.name)
        os.unlink(stderr_log.name)
        pytest.fail("Server did not start in time. See logs above.")

    yield
    
    # Teardown: stop the server and clean up logs
    server_process.terminate()
    server_process.join()
    os.unlink(stdout_log.name)
    os.unlink(stderr_log.name)

def test_manifest_endpoint(mcp_server):
    """
    Tests if the root endpoint returns the MCP manifest.
    """
    response = requests.get(f"{BASE_URL}/")
    assert response.status_code == 200
    manifest = response.json()
    assert manifest["mcp_version"] == "1.0"
    assert "text_to_speech" in manifest["tools"]
    assert "speech_to_text" in manifest["tools"]

def test_tts_and_stt_end_to_end(mcp_server):
    """
    Tests the full TTS -> Audio -> STT loop and plays the audio.
    """
    # --- 1. Test TTS Endpoint ---
    test_text = "Hello from your Model Context Protocol server."
    tts_response = requests.post(f"{BASE_URL}/tts", params={"text": test_text})
    
    assert tts_response.status_code == 200
    assert tts_response.headers["content-type"] == "audio/wav"
    
    # Save the audio to a file
    audio_output_path = "test_output.wav"
    with open(audio_output_path, "wb") as f:
        f.write(tts_response.content)
        
    assert os.path.exists(audio_output_path)
    print(f"\n✅ TTS audio saved to {audio_output_path}")

    # --- 2. Play the audio to "hear" the result ---
    print(f"\n🔊 Playing audio... (You should hear: '{test_text}')")
    try:
        # Using afplay for macOS
        subprocess.run(["afplay", audio_output_path], check=True, capture_output=True)
        print("✅ Audio playback completed.")
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"⚠️ Could not play audio automatically: {e}")
        print("You can play 'test_output.wav' manually to hear the result.")

    # --- 3. Test STT Endpoint ---
    print("\n🎤 Testing STT by sending the generated audio back...")
    with open(audio_output_path, "rb") as f:
        files = {'audio_file': (audio_output_path, f, 'audio/wav')}
        stt_response = requests.post(f"{BASE_URL}/stt", files=files)

    assert stt_response.status_code == 200
    transcription = stt_response.json()["transcription"]
    
    print(f"\n📝 Transcription result: '{transcription}'")
    
    # The placeholder STT function returns a fixed string.
    # In a real test with a functional STT, you'd assert this:
    # assert test_text.lower() in transcription.lower()
    assert transcription == "simulated transcription of audio"
    print("✅ STT endpoint returned the expected (simulated) transcription.")

    # --- 4. Cleanup ---
    os.remove(audio_output_path)

