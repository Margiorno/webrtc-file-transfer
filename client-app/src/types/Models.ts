export type ClientId = string;
export type ClientSecret = string;
export type RoomCode = string;

export type RoomState = 
    | 'WAITING_FOR_GUEST'
    | 'PENDING_HOST_APPROVAL'
    | 'PENDING_2FA'
    | 'ACTIVE'
    | 'CLOSED';

export interface CreateRoomResponse {
    roomCode: RoomCode;
    hostId: ClientId;
    hostSecret: ClientSecret;
    state: RoomState;
}

export interface JoinRoomResponse {
    guestId: ClientId;
    guestSecret: ClientSecret;
    state: RoomState;
}

export type WsMessage =
    | { type: 'AUTH'; clientId: ClientId; clientSecret: ClientSecret }
    | { type: 'AUTH_SUCCESS'; state: RoomState }
    | { type: 'GUEST_JOIN_REQUESTED'; deviceType: string; os: string; browser: string }
    | { type: 'TWO_FACTOR_CHALLENGE'; options: string[] }
    | { type: 'ROOM_ACTIVE'; state: RoomState }
    | { type: 'SDP_OFFER'; sdpPayload: string }
    | { type: 'SDP_ANSWER'; sdpPayload: string }
    | { type: 'ICE_CANDIDATE'; candidate: string }; 