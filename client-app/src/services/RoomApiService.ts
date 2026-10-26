import type {
  ClientId,
  ClientSecret,
  CreateRoomResponse,
  JoinDecision,
  JoinRoomResponse,
  ResolveJoinResponse,
  RoomCode,
  SecurityMode,
  Submit2FAResponse,
} from '../types/Models';

export class RoomApiService {
  private readonly baseUrl = 'http://localhost:8080/api/rooms';

  public async createRoom(securityMode: SecurityMode = 'STANDARD'): Promise<CreateRoomResponse> {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ securityMode }),
    });

    if (!response.ok) {
      throw new Error(`Failed to create room: ${response.statusText}`);
    }

    return await response.json();
  }

  public async joinRoom(roomCode: RoomCode): Promise<JoinRoomResponse> {
    const response = await fetch(`${this.baseUrl}/${roomCode}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });

    if (!response.ok) {
      throw new Error(`Failed to join room: ${response.statusText}`);
    }

    return await response.json();
  }

  public async resolveJoin(
    roomCode: RoomCode,
    hostId: ClientId,
    hostSecret: ClientSecret,
    decision: JoinDecision
  ): Promise<ResolveJoinResponse> {
    const response = await fetch(`${this.baseUrl}/${roomCode}/resolve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Id': hostId,
        'X-Client-Secret': hostSecret,
      },
      body: JSON.stringify({ decision }),
    });

    if (!response.ok) {
      throw new Error(`Failed to resolve join request: ${response.statusText}`);
    }

    return await response.json();
  }

  public async submit2FA(
    roomCode: RoomCode,
    guestId: ClientId,
    guestSecret: ClientSecret,
    code: string
  ): Promise<Submit2FAResponse> {
    const response = await fetch(`${this.baseUrl}/${roomCode}/2fa`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Client-Id': guestId,
        'X-Client-Secret': guestSecret,
      },
      body: JSON.stringify({ code }),
    });

    if (!response.ok) {
      throw new Error(`2FA verification failed: ${response.statusText}`);
    }

    return await response.json();
  }
}

export const roomApiService = new RoomApiService();