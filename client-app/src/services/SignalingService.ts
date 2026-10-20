import type { WsMessage } from "../types/Models";


export class SignalingService {
    private ws: WebSocket | null = null;
    private readonly wsUrl = 'ws://localhost:8080/ws/signaling';

    public connect(clientId: string, clientSecret: string): Promise<void> {
        return new Promise((resolve, reject) => {
            this.ws = new WebSocket(this.wsUrl);

            this.ws.onopen = () => {
                console.log('[SignalingService] WebSocket connected. Sending AUTH frame...');

                this.sendMessage({
                    type: 'AUTH',
                    clientId: clientId,
                    clientSecret: clientSecret
                });
            };

            this.ws.onmessage = (event) => {
                const message: WsMessage = JSON.parse(event.data);
                console.log('[SignalingService] Received:', message);

                if (message.type === 'AUTH_SUCCESS') {
                    console.log('[SignalingService] Authentication successful. Connection established.');
                    resolve();
                }

                // Future logic for handling GUEST_JOIN_REQUESTED, SDP, etc. will go here
                // or be delegated to event listeners.

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

    public sendMessage(message: WsMessage) {
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