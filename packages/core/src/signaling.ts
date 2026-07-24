import {
  PROTOCOL_VERSION,
  isVersionMismatch,
  parseServerFrame,
  type ServerFrame
} from '@sauti/protocol';
import { computeBackoff } from './backoff.js';
import { DisconnectedError } from './errors.js';
import type { SautiRuntime, SocketLike } from './types.js';

export interface SignalingHooks {
  onFrame(frame: ServerFrame): void;
  onVersionMismatch(raw: unknown): void;
  onReconnecting(): void;
  onTerminal(error: DisconnectedError): void;
}

export interface SignalingConfig {
  maxReconnectAttempts: number;
  reconnectBaseMs: number;
  reconnectMaxMs: number;
  random: () => number;
}

export class SignalingClient {
  private socket: SocketLike | null = null;
  private url = '';
  private token = '';
  private attempt = 0;
  private intentional = false;
  private terminated = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly runtime: SautiRuntime,
    private readonly config: SignalingConfig,
    private readonly hooks: SignalingHooks
  ) {}

  connect(url: string, token: string): void {
    this.url = url;
    this.token = token;
    this.intentional = false;
    this.terminated = false;
    this.attempt = 0;
    this.open();
  }

  private open(): void {
    const socket = this.runtime.createWebSocket(this.url);
    this.socket = socket;
    socket.onopen = () => {
      socket.send(
        JSON.stringify({ v: PROTOCOL_VERSION, type: 'join', token: this.token })
      );
    };
    socket.onmessage = (ev) => this.receive(ev.data);
    socket.onerror = () => undefined;
    socket.onclose = () => this.handleClose();
  }

  private receive(data: string): void {
    let raw: unknown;
    try {
      raw = JSON.parse(data);
    } catch {
      return;
    }
    if (isVersionMismatch(raw)) {
      this.hooks.onVersionMismatch(raw);
      return;
    }
    const parsed = parseServerFrame(raw);
    if (!parsed.success) return;
    if (parsed.data.type === 'ready') this.attempt = 0;
    this.hooks.onFrame(parsed.data);
  }

  private handleClose(): void {
    this.socket = null;
    if (this.intentional || this.terminated) return;
    if (this.attempt >= this.config.maxReconnectAttempts) {
      this.terminated = true;
      this.hooks.onTerminal(
        new DisconnectedError('reconnection gave up after the attempt budget')
      );
      return;
    }
    this.hooks.onReconnecting();
    const delay = computeBackoff(
      this.attempt,
      this.config.reconnectBaseMs,
      this.config.reconnectMaxMs,
      this.config.random
    );
    this.attempt += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.intentional && !this.terminated) this.open();
    }, delay);
  }

  send(frame: unknown): void {
    if (this.socket) this.socket.send(JSON.stringify(frame));
  }

  close(): void {
    this.intentional = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    const socket = this.socket;
    this.socket = null;
    socket?.close();
  }
}
