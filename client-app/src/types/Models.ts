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
    securityMode: string;
}

export interface JoinRoomResponse {
    roomCode?: RoomCode;
    guestId: ClientId;
    guestSecret: ClientSecret;
    state: RoomState;
}

export interface IncomingWsFrame {
    type:
    | 'AUTH_SUCCESS'
    | 'GUEST_JOIN_REQUESTED'
    | 'TWO_FACTOR_CHALLENGE'
    | 'ROOM_ACTIVE'
    | 'SDP_OFFER'
    | 'SDP_ANSWER'
    | 'ICE_CANDIDATE'
    | 'PEER_DISCONNECTED'
    | 'ERROR';
    roomCode: RoomCode;
    state?: RoomState | null;
    payload?: unknown;
}

export interface OutgoingWsFrame {
    type: 'AUTH' | 'SDP_OFFER' | 'SDP_ANSWER' | 'ICE_CANDIDATE';
    clientId: ClientId;
    clientSecret: ClientSecret;
    payload?: unknown;
}