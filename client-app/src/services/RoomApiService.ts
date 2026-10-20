import type { CreateRoomResponse, JoinRoomResponse } from '../types/Models';

export class RoomApiService {
    private readonly baseUrl = 'http://localhost:8080/api/rooms';

    public async createRoom(): Promise<CreateRoomResponse> {
        const response = await fetch(this.baseUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ securityMode: 'STANDARD' }),
        });

        if (!response.ok) {
            throw new Error(`Failed to create room: ${response.statusText}`);
        }

        return await response.json();
    }

    public async joinRoom(roomCode: string): Promise<JoinRoomResponse> {
        const response = await fetch(`${this.baseUrl}/${roomCode}/join`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error(`Failed to join room: ${response.statusText}`);
        }

        return await response.json();
    }
}

export const roomApiService = new RoomApiService();