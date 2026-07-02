/**
 * 間 AI WebSocket Bridge
 *
 * Communication bridge for local development/standalone mode.
 * Connects to avatar_server.py via WebSocket (port 8081).
 */

import type { AvatarState } from "../animation/AvatarState";
import { parseEmotion, parseActivity } from "../animation/AvatarState";

/**
 * State change callback
 */
export type StateChangeCallback = (state: Partial<AvatarState>) => void;

/**
 * Model change callback
 */
export type ModelChangeCallback = (modelId: string) => void;

/**
 * Connection status callback
 */
export type ConnectionCallback = (connected: boolean) => void;

/**
 * WebSocket Bridge Options
 */
export interface WebSocketBridgeOptions {
  /** WebSocket server URL (default: ws://localhost:8081) */
  url?: string;
  /** Auto-reconnect on disconnect (default: true) */
  autoReconnect?: boolean;
  /** Reconnect interval in ms (default: 3000) */
  reconnectInterval?: number;
}

/**
 * WebSocket Bridge
 *
 * Handles real-time communication with avatar_server.py for local testing
 * and standalone popover mode.
 */
export class WebSocketBridge {
  private ws: WebSocket | null = null;
  private url: string;
  private autoReconnect: boolean;
  private reconnectInterval: number;
  private reconnectTimer: number | null = null;

  private onStateChange: StateChangeCallback | null = null;
  private onModelChange: ModelChangeCallback | null = null;
  private onConnection: ConnectionCallback | null = null;

  constructor(options: WebSocketBridgeOptions = {}) {
    this.url = options.url ?? "ws://localhost:8081";
    this.autoReconnect = options.autoReconnect ?? true;
    this.reconnectInterval = options.reconnectInterval ?? 3000;
  }

  /**
   * Connect to WebSocket server
   */
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return;

    console.log(`[WebSocketBridge] Connecting to ${this.url}...`);

    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log("[WebSocketBridge] Connected!");
      this.onConnection?.(true);

      // Identify as avatar UI client
      this.send({
        type: "identify",
        client_type: "avatar_ui",
      });
    };

    this.ws.onclose = () => {
      console.log("[WebSocketBridge] Disconnected");
      this.onConnection?.(false);

      if (this.autoReconnect) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error("[WebSocketBridge] Error:", error);
    };

    this.ws.onmessage = (event) => {
      this.handleMessage(event.data);
    };
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    this.autoReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
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
   * Register callback for connection status changes
   */
  onConnectionChange(callback: ConnectionCallback): void {
    this.onConnection = callback;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(raw: string): void {
    try {
      const data = JSON.parse(raw);
      const type = data.type;

      console.log(`[WebSocketBridge] Received: ${type}`, data);

      switch (type) {
        case "welcome":
          // Server acknowledged connection
          console.log("[WebSocketBridge] Server welcome:", data.message);
          break;

        case "emotion":
          // Emotion update from avatar_server.py
          if (this.onStateChange) {
            const partialState: Partial<AvatarState> = {};

            if (data.emotion) {
              partialState.emotion = parseEmotion(data.emotion.toUpperCase());
            }
            if (typeof data.intensity === "number") {
              // Convert 0-100 to 0-1
              partialState.intensity = data.intensity / 100;
            }
            if (data.message) {
              partialState.message = data.message;
            }

            this.onStateChange(partialState);
          }
          break;

        case "state":
          // Activity state update
          if (this.onStateChange && data.state) {
            const activityMap: Record<string, string> = {
              idle: "IDLE",
              thinking: "THINKING",
              speaking: "SPEAKING",
              listening: "LISTENING",
              loading: "THINKING",
              error: "ERROR",
              generating: "GENERATING",
            };
            const activity = activityMap[data.state.toLowerCase()] ?? "IDLE";
            this.onStateChange({ activity: parseActivity(activity) });
          }
          break;

        case "model":
          // Model change
          if (this.onModelChange && data.model_id) {
            this.onModelChange(data.model_id);
          }
          break;

        case "classification":
          // AI classification (can map to emotion)
          if (this.onStateChange && data.intent) {
            // Map intents to emotions
            const intentEmotionMap: Record<string, string> = {
              greeting: "HAPPY",
              help_request: "HAPPY",
              factual_query: "THINKING",
              creative_writing: "EXCITED",
              code_debugging: "THINKING",
              casual_conversation: "HAPPY",
            };
            const emotion = intentEmotionMap[data.intent] ?? "NEUTRAL";
            this.onStateChange({
              emotion: parseEmotion(emotion),
              intensity: data.confidence ?? 0.7,
            });
          }
          break;

        case "thinking_phase":
          // AI thinking phase
          if (this.onStateChange) {
            this.onStateChange({ activity: "THINKING" });
          }
          break;

        case "chat_ai_start":
          if (this.onStateChange) {
            this.onStateChange({ activity: "GENERATING" });
          }
          break;

        case "chat_ai_complete":
          if (this.onStateChange) {
            this.onStateChange({ activity: "IDLE" });
          }
          break;

        case "pong":
          // Heartbeat response
          break;
      }
    } catch (e) {
      console.error("[WebSocketBridge] Parse error:", e);
    }
  }

  /**
   * Send message to server
   */
  private send(data: Record<string, unknown>): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data));
    }
  }

  /**
   * Schedule reconnection attempt
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;

    console.log(
      `[WebSocketBridge] Reconnecting in ${this.reconnectInterval}ms...`
    );
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, this.reconnectInterval);
  }

  /**
   * Dispose and clean up
   */
  dispose(): void {
    this.disconnect();
  }
}
