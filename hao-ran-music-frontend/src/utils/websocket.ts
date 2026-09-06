import { usePlayerStore } from '@/stores/player';
import { useUserStore } from '@/stores/user';
import { logger as frontendLogger } from '@/utils/logger';
export interface WebSocketMessage {
    type: string;
    version?: number;
    messageId?: string;
    traceId?: string;
    data?: any;
    payload?: any;
    timestamp?: number;
    userId?: string | number;
    clientTimestamp?: number;
    latencyMs?: number;
    route?: string;
    pageVisible?: boolean;
    visibilityState?: string;
    focused?: boolean;
    navigatorOnline?: boolean;
    playerState?: string;
    playing?: boolean;
    currentSongId?: string | number;
    currentTime?: number;
    duration?: number;
    playbackRate?: number;
}
export class WebSocketClient {
    private ws: WebSocket | null = null;
    private url = '';
    private protocols: string[] = [];
    private reconnectTimer: number | null = null;
    private heartbeatTimer: number | null = null;
    private reconnectAttempts = 0;
    private heartbeatInterval = 30000;
    private lastPongAt = 0;
    private isManualClose = false;
    private presenceListener: (() => void) | null = null;
    private networkReconnectListener: (() => void) | null = null;
    private seenMessageIds = new Set<string>();
    constructor() {
        this.buildUrl();
    }
    private buildUrl() {
        const userStore = useUserStore();
        const userId = userStore.userId;
        if (!userId) {
            frontendLogger.capture('warn', '[WebSocket] 用户未登录，跳过连接');
            this.url = '';
            return;
        }
        const wsUrl = import.meta.env.VITE_WS_URL;
        this.protocols = [];
        if (wsUrl) {
            this.url = `${wsUrl}/notifications`;
            return;
        }
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.url = `${protocol}//${window.location.host}/api/ws/notifications`;
    }
    connect() {
        if (this.ws?.readyState === WebSocket.OPEN || this.ws?.readyState === WebSocket.CONNECTING) {
            return;
        }
        if (!this.url) {
            this.buildUrl();
            if (!this.url) {
                frontendLogger.capture('warn', '[WebSocket] URL为空，无法连接');
                return;
            }
        }
        this.isManualClose = false;
        this.setupNetworkReconnectListener();
        try {
            const socket = this.protocols.length > 0
                ? new WebSocket(this.url, this.protocols)
                : new WebSocket(this.url);
            this.ws = socket;
            socket.onopen = () => {
                if (import.meta.env.DEV) {
                    frontendLogger.capture('debug', '[WebSocket] 已连接');
                }
                if (this.reconnectTimer) {
                    window.clearTimeout(this.reconnectTimer);
                    this.reconnectTimer = null;
                }
                this.reconnectAttempts = 0;
                this.lastPongAt = Date.now();
                this.setupPresenceListeners();
                this.startHeartbeat();
                this.sendPresence('connected');
            };
            socket.onmessage = (event) => {
                try {
                    const data: WebSocketMessage = JSON.parse(event.data);
                    if (data.messageId) {
                        if (this.seenMessageIds.has(data.messageId)) {
                            return;
                        }
                        this.seenMessageIds.add(data.messageId);
                        if (this.seenMessageIds.size > 256) {
                            const oldest = this.seenMessageIds.values().next().value;
                            if (oldest) {
                                this.seenMessageIds.delete(oldest);
                            }
                        }
                    }
                    const pongClientTimestamp = data.clientTimestamp ?? data.data?.clientTimestamp;
                    if (data.type === 'pong' && pongClientTimestamp) {
                        this.lastPongAt = Date.now();
                        data.latencyMs = Date.now() - Number(pongClientTimestamp);
                    }
                    window.dispatchEvent(new CustomEvent('websocket-message', { detail: data }));
                }
                catch (error) {
                    frontendLogger.capture('error', '[WebSocket] 解析消息失败:', error);
                }
            };
            socket.onerror = (error) => {
                frontendLogger.capture('error', '[WebSocket] 错误:', error);
            };
            socket.onclose = (event) => {
                if (import.meta.env.DEV) {
                    frontendLogger.capture('debug', '[WebSocket] 已断开:', event.code, event.reason);
                }
                const isCurrentSocket = this.ws === socket;
                if (!isCurrentSocket) {
                    return;
                }
                this.ws = null;
                this.stopHeartbeat();
                this.cleanupPresenceListeners();
                if (!this.isManualClose) {
                    this.scheduleReconnect();
                }
            };
        }
        catch (error) {
            frontendLogger.capture('error', '[WebSocket] 连接失败:', error);
        }
    }
    disconnect() {
        this.isManualClose = true;
        this.sendPresence('disconnecting');
        this.stopHeartbeat();
        this.cleanupPresenceListeners();
        this.cleanupNetworkReconnectListener();
        if (this.reconnectTimer) {
            window.clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }
    reconnect() {
        this.disconnect();
        this.buildUrl();
        this.connect();
    }
    send(message: string | object) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            const data = typeof message === 'string' ? message : JSON.stringify(message);
            this.ws.send(data);
            return;
        }
        frontendLogger.capture('warn', '[WebSocket] 未连接，无法发送消息');
    }
    private startHeartbeat() {
        this.stopHeartbeat();
        this.sendPresence('heartbeat');
        this.heartbeatTimer = window.setInterval(() => {
            if (Date.now() - this.lastPongAt > this.heartbeatInterval * 2) {
                this.ws?.close(4000, 'heartbeat_timeout');
                return;
            }
            this.sendPresence('heartbeat');
        }, this.heartbeatInterval);
    }
    private stopHeartbeat() {
        if (this.heartbeatTimer) {
            window.clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }
    private setupPresenceListeners() {
        this.cleanupPresenceListeners();
        this.presenceListener = () => this.sendPresence('state_change');
        document.addEventListener('visibilitychange', this.presenceListener);
        window.addEventListener('focus', this.presenceListener);
        window.addEventListener('blur', this.presenceListener);
        window.addEventListener('online', this.presenceListener);
        window.addEventListener('offline', this.presenceListener);
    }
    private cleanupPresenceListeners() {
        if (!this.presenceListener) {
            return;
        }
        document.removeEventListener('visibilitychange', this.presenceListener);
        window.removeEventListener('focus', this.presenceListener);
        window.removeEventListener('blur', this.presenceListener);
        window.removeEventListener('online', this.presenceListener);
        window.removeEventListener('offline', this.presenceListener);
        this.presenceListener = null;
    }
    private setupNetworkReconnectListener() {
        if (this.networkReconnectListener) {
            return;
        }
        this.networkReconnectListener = () => {
            if (this.isManualClose || !navigator.onLine) {
                return;
            }
            if (this.ws?.readyState === WebSocket.OPEN || this.ws?.readyState === WebSocket.CONNECTING) {
                this.sendPresence('state_change');
                return;
            }
            if (this.reconnectTimer) {
                window.clearTimeout(this.reconnectTimer);
                this.reconnectTimer = null;
            }
            this.reconnectAttempts = 0;
            this.buildUrl();
            this.connect();
        };
        window.addEventListener('online', this.networkReconnectListener);
    }
    private cleanupNetworkReconnectListener() {
        if (!this.networkReconnectListener) {
            return;
        }
        window.removeEventListener('online', this.networkReconnectListener);
        this.networkReconnectListener = null;
    }
    private sendPresence(event: string) {
        if (this.ws?.readyState !== WebSocket.OPEN) {
            return;
        }
        this.send(this.buildPresenceMessage(event));
    }
    private buildPresenceMessage(event: string): WebSocketMessage & Record<string, unknown> {
        const playerStore = usePlayerStore();
        const currentSong = playerStore.currentSong;
        const playing = Boolean(playerStore.playing);
        const mvPlaying = Boolean(playerStore.mvPlaying);
        return {
            type: 'ping',
            event,
            clientTimestamp: Date.now(),
            route: `${window.location.pathname}${window.location.search}`,
            pageVisible: document.visibilityState === 'visible',
            visibilityState: document.visibilityState,
            focused: document.hasFocus(),
            navigatorOnline: navigator.onLine,
            reconnectAttempts: this.reconnectAttempts,
            heartbeatIntervalMs: this.heartbeatInterval,
            playerState: this.resolvePlayerState(Boolean(currentSong), playing, mvPlaying),
            playing,
            mvPlaying,
            currentSongId: currentSong?.id,
            currentTime: playerStore.currentTime,
            duration: playerStore.duration || currentSong?.duration || 0,
            playbackRate: playerStore.playbackRate
        };
    }
    private resolvePlayerState(hasSong: boolean, playing: boolean, mvPlaying: boolean) {
        if (mvPlaying) {
            return playing ? 'mv_playing' : 'mv_paused';
        }
        if (playing) {
            return 'playing';
        }
        if (hasSong) {
            return 'paused';
        }
        return 'idle';
    }
    private scheduleReconnect() {
        if (this.reconnectTimer) {
            window.clearTimeout(this.reconnectTimer);
        }
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
        this.reconnectTimer = window.setTimeout(() => {
            this.reconnectTimer = null;
            this.reconnectAttempts++;
            this.connect();
        }, delay);
    }
}
let wsClient: WebSocketClient | null = null;
export function useWebSocket() {
    if (!wsClient) {
        wsClient = new WebSocketClient();
    }
    return wsClient;
}
export function initWebSocket(_store?: unknown) {
    const client = useWebSocket();
    client.connect();
    return client;
}
export function closeWebSocket() {
    if (wsClient) {
        wsClient.disconnect();
        wsClient = null;
    }
}
