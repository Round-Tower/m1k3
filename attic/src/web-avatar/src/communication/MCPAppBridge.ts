/**
 * 間 AI MCP App Bridge
 *
 * Communication bridge for MCP Apps (Claude Desktop iframe).
 * Uses postMessage protocol for state synchronization.
 */

import type { AvatarState, AvatarEmotion, AvatarActivity } from "../animation/AvatarState";
import { parseEmotion, parseActivity } from "../animation/AvatarState";

/**
 * Message types from MCP server to avatar
 */
export type IncomingMessageType =
  | "avatar_update"     // Update avatar state
  | "voice_status"      // TTS/STT status
  | "tts_event"         // TTS playback events
  | "stt_result"        // Speech recognition result
  | "model_change";     // Change avatar model

/**
 * Message types from avatar to MCP server
 */
export type OutgoingMessageType =
  | "mcp_app_ready"        // App initialized and ready
  | "request_voice_input"  // Request STT
  | "avatar_interaction"   // User interacted with avatar
  | "state_sync";          // Sync current state

/**
 * Incoming message from MCP server
 */
export interface IncomingMessage {
  type: IncomingMessageType;
  payload: AvatarUpdatePayload | VoiceStatusPayload | TTSEventPayload | STTResultPayload | ModelChangePayload;
}

export interface AvatarUpdatePayload {
  emotion?: string;
  activity?: string;
  intensity?: number;
  message?: string;
}

export interface VoiceStatusPayload {
  tts_engine: string;
  stt_engine: string;
  is_speaking: boolean;
  is_listening: boolean;
}

export interface TTSEventPayload {
  event: "start" | "chunk" | "complete" | "error";
  text?: string;
  progress?: number;
}

export interface STTResultPayload {
  text: string;
  confidence: number;
  is_final: boolean;
}

export interface ModelChangePayload {
  model_id: string;
}

/**
 * Outgoing message to MCP server
 */
export interface OutgoingMessage {
  type: OutgoingMessageType;
  payload?: Record<string, unknown>;
}

/**
 * State change callback
 */
export type StateChangeCallback = (state: Partial<AvatarState>) => void;

/**
 * Model change callback
 */
export type ModelChangeCallback = (modelId: string) => void;

/**
 * MCP App Bridge
 *
 * Handles postMessage communication with Claude Desktop host.
 */
export class MCPAppBridge {
  private onStateChange: StateChangeCallback | null = null;
  private onModelChange: ModelChangeCallback | null = null;
  private isReady = false;

  constructor() {
    // Listen for messages from parent window
    window.addEventListener("message", this.handleMessage);
  }

  /**
   * Register callback for avatar state changes
   */
  onAvatarStateChange(callback: StateChangeCallback): void {
    this.onStateChange = callback;
  }

  /**
   * Register callback for model changes
   */
  onAvatarModelChange(callback: ModelChangeCallback): void {
    this.onModelChange = callback;
  }

  /**
   * Signal that the app is ready to receive messages
   */
  signalReady(): void {
    if (this.isReady) return;
    this.isReady = true;
    this.sendMessage({
      type: "mcp_app_ready",
    });
  }

  /**
   * Request voice input from the MCP server
   */
  requestVoiceInput(timeoutSeconds = 5): void {
    this.sendMessage({
      type: "request_voice_input",
      payload: { timeout: timeoutSeconds },
    });
  }

  /**
   * Report user interaction with avatar
   */
  reportInteraction(action: string, data?: Record<string, unknown>): void {
    this.sendMessage({
      type: "avatar_interaction",
      payload: { action, data },
    });
  }

  /**
   * Sync current avatar state back to server
   */
  syncState(state: AvatarState): void {
    this.sendMessage({
      type: "state_sync",
      payload: {
        emotion: state.emotion,
        activity: state.activity,
        intensity: state.intensity,
        message: state.message,
      },
    });
  }

  /**
   * Handle incoming message from parent window
   */
  private handleMessage = (event: MessageEvent): void => {
    // Validate message structure
    const data = event.data as IncomingMessage;
    if (!data || typeof data.type !== "string") return;

    switch (data.type) {
      case "avatar_update": {
        const payload = data.payload as AvatarUpdatePayload;
        const partialState: Partial<AvatarState> = {};

        if (payload.emotion) {
          partialState.emotion = parseEmotion(payload.emotion);
        }
        if (payload.activity) {
          partialState.activity = parseActivity(payload.activity);
        }
        if (typeof payload.intensity === "number") {
          partialState.intensity = payload.intensity;
        }
        if (payload.message) {
          partialState.message = payload.message;
        }

        if (this.onStateChange && Object.keys(partialState).length > 0) {
          this.onStateChange(partialState);
        }
        break;
      }

      case "model_change": {
        const payload = data.payload as ModelChangePayload;
        if (this.onModelChange && payload.model_id) {
          this.onModelChange(payload.model_id);
        }
        break;
      }

      case "tts_event": {
        const payload = data.payload as TTSEventPayload;
        // Map TTS events to avatar activity
        if (this.onStateChange) {
          if (payload.event === "start") {
            this.onStateChange({ activity: "SPEAKING" });
          } else if (payload.event === "complete") {
            this.onStateChange({ activity: "IDLE" });
          } else if (payload.event === "error") {
            this.onStateChange({ activity: "ERROR" });
          }
        }
        break;
      }

      case "voice_status": {
        const payload = data.payload as VoiceStatusPayload;
        if (this.onStateChange) {
          if (payload.is_speaking) {
            this.onStateChange({ activity: "SPEAKING" });
          } else if (payload.is_listening) {
            this.onStateChange({ activity: "LISTENING" });
          }
        }
        break;
      }

      case "stt_result": {
        // STT result can be forwarded to application logic
        // For now, just update activity when final result received
        const payload = data.payload as STTResultPayload;
        if (this.onStateChange && payload.is_final) {
          this.onStateChange({ activity: "THINKING" });
        }
        break;
      }
    }
  };

  /**
   * Send message to parent window
   */
  private sendMessage(message: OutgoingMessage): void {
    if (window.parent !== window) {
      window.parent.postMessage(message, "*");
    }
  }

  /**
   * Dispose and clean up
   */
  dispose(): void {
    window.removeEventListener("message", this.handleMessage);
  }
}
