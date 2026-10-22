import { useState, useEffect } from 'react';
import { roomApiService } from './services/RoomApiService';
import { signalingService } from './services/SignalingService';
import { LobbyView } from './components/LobbyView';
import { RoomView } from './components/RoomView';
import type {
  ClientId,
  ClientSecret,
  DeviceMetadata,
  JoinDecision,
  RoomCode,
  RoomState,
  SecurityMode,
} from './types/Models';

function App() {
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [role, setRole] = useState<'HOST' | 'GUEST' | null>(null);
  const [roomCode, setRoomCode] = useState<RoomCode | null>(null);
  const [roomState, setRoomState] = useState<RoomState | null>(null);
  const [clientId, setClientId] = useState<ClientId | null>(null);
  const [clientSecret, setClientSecret] = useState<ClientSecret | null>(null);

  const [guestMetadata, setGuestMetadata] = useState<DeviceMetadata | null>(null);
  const [hostTwoFactorCode, setHostTwoFactorCode] = useState<string | null>(null);
  const [guestTwoFactorOptions, setGuestTwoFactorOptions] = useState<string[]>([]);

  useEffect(() => {
    const unsubscribe = signalingService.onMessage((msg) => {
      if (msg.state) {
        setRoomState(msg.state);
      }

      if (msg.type === 'GUEST_JOIN_REQUESTED') {
        setRoomState('PENDING_HOST_APPROVAL');
        setGuestMetadata((msg.payload as DeviceMetadata) ?? null);
      }

      if (msg.type === 'TWO_FACTOR_CHALLENGE') {
        setRoomState('PENDING_2FA');
        const payload = msg.payload as { options?: string[] } | string[] | undefined;
        const options = Array.isArray(payload) ? payload : payload?.options ?? [];
        setGuestTwoFactorOptions(options);
      }

      if (msg.type === 'ROOM_ACTIVE') {
        setRoomState('ACTIVE');
      }

      if (msg.type === 'JOIN_REJECTED' || msg.type === 'PEER_DISCONNECTED') {
        setRoomState('CLOSED');
      }
    });

    return () => unsubscribe();
  }, []);

  const handleCreateRoom = async (securityMode: SecurityMode) => {
    try {
      setIsLoading(true);
      const response = await roomApiService.createRoom(securityMode);
      await signalingService.connect(response.roomCode, response.hostId, response.hostSecret);

      setRole('HOST');
      setRoomCode(response.roomCode);
      setRoomState(response.state);
      setClientId(response.hostId);
      setClientSecret(response.hostSecret);
    } catch (error) {
      console.error('Failed to create room:', error);
      alert('Error creating room');
    } finally {
      setIsLoading(false);
    }
  };

  const handleJoinRoom = async (codeToJoin: RoomCode) => {
    try {
      setIsLoading(true);
      const response = await roomApiService.joinRoom(codeToJoin);
      await signalingService.connect(codeToJoin, response.guestId, response.guestSecret);

      setRole('GUEST');
      setRoomCode(codeToJoin);
      setRoomState(response.state);
      setClientId(response.guestId);
      setClientSecret(response.guestSecret);
    } catch (error) {
      console.error('Failed to join room:', error);
      alert('Error joining room. Check if the room code is valid.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleResolveJoin = async (decision: JoinDecision) => {
    if (!roomCode || !clientId || !clientSecret) return;

    try {
      setIsLoading(true);
      const response = await roomApiService.resolveJoin(roomCode, clientId, clientSecret, decision);
      console.log('Resolve join response:', response);

      setRoomState(response.state);
      const code = response.twoFactorCorrectCode ?? response.correctCode ?? null;
      if (code) {
        setHostTwoFactorCode(code);
      }
    } catch (error) {
      console.error('Failed to resolve join:', error);
      alert('Failed to resolve guest request');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit2FA = async (selectedCode: string) => {
    if (!roomCode || !clientId || !clientSecret) return;

    try {
      setIsLoading(true);
      const response = await roomApiService.submit2FA(roomCode, clientId, clientSecret, selectedCode);
      console.log('2FA response:', response);
      setRoomState(response.state);
    } catch (error) {
      console.error('Failed 2FA verification:', error);
      alert('Invalid 2FA code or room closed.');
      setRoomState('CLOSED');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLeaveRoom = () => {
    signalingService.disconnect();
    setRole(null);
    setRoomCode(null);
    setRoomState(null);
    setClientId(null);
    setClientSecret(null);
    setGuestMetadata(null);
    setHostTwoFactorCode(null);
    setGuestTwoFactorOptions([]);
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4">
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">WebRTC File Transfer</h1>
        <p className="text-gray-500">Direct P2P transfer bypassing the server</p>
      </header>

      <main className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
        {roomCode && role && roomState ? (
          <RoomView
            role={role}
            roomCode={roomCode}
            roomState={roomState}
            isLoading={isLoading}
            guestMetadata={guestMetadata}
            hostTwoFactorCode={hostTwoFactorCode}
            guestTwoFactorOptions={guestTwoFactorOptions}
            onResolveJoin={handleResolveJoin}
            onSubmit2FA={handleSubmit2FA}
            onLeaveRoom={handleLeaveRoom}
          />
        ) : (
          <LobbyView
            isLoading={isLoading}
            onCreateRoom={handleCreateRoom}
            onJoinRoom={handleJoinRoom}
          />
        )}
      </main>
    </div>
  );
}

export default App;