export type ClientId = string;
export type ClientSecret = string;
export type RoomCode = string;

export type SecurityMode = 'STANDARD' | 'TWO_FACTOR';
export type JoinDecision = 'ACCEPT' | 'REJECT';

export type RoomState = 
  | 'WAITING_FOR_GUEST'
  | 'PENDING_HOST_APPROVAL'
  | 'PENDING_2FA'
  | 'ACTIVE'
  | 'CLOSED';

export interface DeviceMetadata {
  deviceType?: string;
  os?: string;
  browser?: string;
  rawUserAgent?: string;
}

// REST Responses
export interface CreateRoomResponse {
  roomCode: RoomCode;
  hostId: ClientId;
  hostSecret: ClientSecret;
  state: RoomState;
  securityMode: SecurityMode;
}

export interface JoinRoomResponse {
  roomCode?: RoomCode;
  guestId: ClientId;
  guestSecret: ClientSecret;
  state: RoomState;
}

export interface ResolveJoinResponse {
  roomCode?: RoomCode;
  state: RoomState;
  twoFactorCorrectCode?: string;
  correctCode?: string;
}

export interface Submit2FAResponse {
  roomCode?: RoomCode;
  state: RoomState;
}

// WebSocket Frames
export interface IncomingWsFrame {
  type: 
    | 'AUTH_SUCCESS'
    | 'GUEST_JOIN_REQUESTED'
    | 'JOIN_REJECTED'
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