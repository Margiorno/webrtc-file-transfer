import type { RoomCode, IncomingWsFrame, OutgoingWsFrame, ClientSecret, ClientId } from "../types/Models";

type MessageListener = (message: IncomingWsFrame) => void;

export class SignalingService {
    private ws: WebSocket | null = null;
    private readonly baseWsUrl = 'ws://localhost:8080/ws/signaling';
    private listeners: Set<MessageListener> = new Set();

    public connect(roomCode: RoomCode, clientId: string, clientSecret: string): Promise<void> {
        return new Promise((resolve, reject) => {
            this.ws = new WebSocket(`${this.baseWsUrl}/${roomCode}`);

            this.ws.onopen = () => {
                console.log('[SignalingService] WebSocket connected. Sending AUTH frame...');

                this.sendMessage({
                    type: 'AUTH',
                    clientId: clientId,
                    clientSecret: clientSecret
                });
            };

            this.ws.onmessage = (event) => {
                const message: IncomingWsFrame = JSON.parse(event.data);
                console.log('[SignalingService] Received:', message);

                if (message.type === 'AUTH_SUCCESS') {
                    console.log('[SignalingService] Authentication successful. Connection established.');
                    resolve();
                } else if (message.type === 'ERROR') {
                    reject(new Error(JSON.stringify(message.payload)));
                }

                this.listeners.forEach((listener) => listener(message));
            };

            this.ws.onerror = (error) => {
                console.error('[SignalingService] WebSocket error:', error);
                reject(error);
            };

            this.ws.onclose = () => {
                console.log('[SignalingService] WebSocket disconnected.');
            };
        });
    }

    onMessage(listener: MessageListener) {
        this.listeners.add(listener);

        return () => {
            this.listeners.delete(listener);
        }
    }

    private saveSessionCredentials(roomCode: RoomCode, clientId: ClientId, clientSecret: ClientSecret) {
        sessionStorage.setItem('webrtc_room_code', roomCode);
        sessionStorage.setItem('webrtc_client_id', clientId);
        sessionStorage.setItem('webrtc_client_secret', clientSecret);
    }

    public sendMessage(message: OutgoingWsFrame) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.error('[SignalingService] WebSocket is not open. Cannot send message');
        }
    }

    public disconnect() {
        this.ws?.close();
        this.ws = null;
    }
}

export const signalingService = new SignalingService();