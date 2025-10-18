import uvicorn
from fastapi import FastAPI, UploadFile, File
from fastapi.responses import JSONResponse, StreamingResponse
import tempfile
import os
import traceback
import numpy as np
from scipy.io.wavfile import write as write_wav

# M1K3-specific imports
from src.engines.stt.stt_manager import STTManager
from src.engines.voice.intelligent_tts_engine import IntelligentTTSEngine

# --- Initialize M1K3 Engines ---
# This assumes default configuration is sufficient.
# If specific configs are needed, this part might need adjustment.
stt_manager = STTManager()
# Note: IntelligentTTSEngine might require specific config or setup.
# For now, we proceed with default initialization.
tts_engine = IntelligentTTSEngine()

app = FastAPI(
    title="M1K3 Model Context Protocol Server",
    description="Exposes M1K3's speech technologies via MCP.",
    version="1.0.0",
)

MCP_MANIFEST = {
    "mcp_version": "1.0",
    "display_name": "M1K3 Speech Services",
    "description": "Provides text-to-speech and text-to-speech capabilities from the M1K3 assistant.",
    "tools": {
        "text_to_speech": {
            "display_name": "Text to Speech",
            "description": "Converts a string of text into spoken audio.",
            "handler": "/tts",
            "inputs": {
                "text": {
                    "type": "string",
                    "required": True,
                    "description": "The text to be converted to speech."
                }
            },
            "outputs": {
                "audio": {
                    "type": "bytes",
                    "media_type": "audio/wav",
                    "description": "The generated audio file."
                }
            }
        },
        "speech_to_text": {
            "display_name": "Speech to Text",
            "description": "Transcribes spoken audio from a file into text.",
            "handler": "/stt",
            "inputs": {
                "audio_file": {
                    "type": "bytes",
                    "media_type": "audio/*",
                    "required": True,
                    "description": "The audio file to be transcribed."
                }
            },
            "outputs": {
                "transcription": {
                    "type": "string",
                    "description": "The transcribed text."
                }
            }
        }
    }
}

@app.get("/", response_model=dict)
async def get_mcp_manifest():
    """
    Provides the MCP manifest, describing the available tools.
    """
    return MCP_MANIFEST

@app.post("/tts")
async def text_to_speech_endpoint(text: str):
    """
    Endpoint for Text-to-Speech.
    Saves the synthesized audio to a temporary file and streams it back.
    """
    audio_data, sample_rate = tts_engine.synthesize(text)

    if audio_data is None:
        return JSONResponse(status_code=500, content={"error": "TTS synthesis failed"})

    # Create a temporary file to store the output audio
    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp_audio_file:
        output_path = tmp_audio_file.name
        write_wav(output_path, sample_rate, audio_data)

    # Stream the file back to the user
    def file_iterator(file_path):
        with open(file_path, "rb") as f:
            yield from f
        os.unlink(file_path) # Clean up the file after streaming

    return StreamingResponse(file_iterator(output_path), media_type="audio/wav")

@app.post("/stt")
async def speech_to_text_endpoint(audio_file: UploadFile = File(...)):
    """
    Endpoint for Speech-to-Text.
    Saves the uploaded audio to a temporary file for transcription.
    """
    try:
        # Create a temporary file to save the uploaded audio
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp_audio_file:
            content = await audio_file.read()
            tmp_audio_file.write(content)
            audio_path = tmp_audio_file.name

        # Transcribe the audio file
        result = stt_manager.transcribe_file(audio_path)
        
        if result is None:
            return JSONResponse(status_code=500, content={"error": "STT transcription failed"})

        transcription = result.text
        
        # Clean up the temporary file
        os.unlink(audio_path)
        
        return JSONResponse(content={"transcription": transcription})
    except Exception as e:
        # Clean up in case of error
        if 'audio_path' in locals() and os.path.exists(audio_path):
            os.unlink(audio_path)
        return JSONResponse(status_code=500, content={"error": str(e)})

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
