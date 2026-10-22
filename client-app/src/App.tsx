import { useState, useEffect } from 'react';
import { roomApiService } from './services/RoomApiService';
import { signalingService } from './services/SignalingService';
import type { RoomCode, RoomState } from './types/Models';

function App() {
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [roomCode, setRoomCode] = useState<RoomCode | null>(null);
  const [roomState, setRoomState] = useState<RoomState | null>(null);
  const [role, setRole] = useState<'HOST' | 'GUEST' | null>(null);
  const [joinCodeInput, setJoinCodeInput] = useState<string>('');
  const [guestMetadata, setGuestMetadata] = useState<unknown>(null);

  useEffect(() => {
    const unsubscribe = signalingService.onMessage((msg) => {
      if (msg.state) {
        setRoomState(msg.state);
      }
      if (msg.type === 'GUEST_JOIN_REQUESTED') {
        setRoomState('PENDING_HOST_APPROVAL');
        setGuestMetadata(msg.payload);
      }
      if (msg.type === 'ROOM_ACTIVE') {
        setRoomState('ACTIVE');
      }
    });

    return () => unsubscribe();
  }, []);

  const handleCreateRoom = async () => {
    try {
      setIsLoading(true);
      const response = await roomApiService.createRoom();
      await signalingService.connect(response.roomCode, response.hostId, response.hostSecret);

      setRole('HOST');
      setRoomCode(response.roomCode);
      setRoomState(response.state);
    } catch (error) {
      console.error('Failed to create room:', error);
      alert('Error creating room');
    } finally {
      setIsLoading(false);
    }
  };

  const handleJoinRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (joinCodeInput.trim().length !== 6) return;

    try {
      setIsLoading(true);
      const cleanCode = joinCodeInput.trim();
      const response = await roomApiService.joinRoom(cleanCode);
      await signalingService.connect(cleanCode, response.guestId, response.guestSecret);

      setRole('GUEST');
      setRoomCode(cleanCode);
      setRoomState(response.state);
    } catch (error) {
      console.error('Failed to join room:', error);
      alert('Error joining room. Check if the room code is valid.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4">
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">WebRTC File Transfer</h1>
        <p className="text-gray-500">Direct P2P transfer bypassing the server</p>
      </header>

      <main className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
        {roomCode ? (
          <div>
            <span className="inline-block px-3 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-800 mb-4">
              Role: {role} | State: {roomState}
            </span>

            <h2 className="text-2xl font-bold text-gray-800 mb-2">Room {roomCode}</h2>

            {role === 'HOST' && roomState === 'WAITING_FOR_GUEST' && (
              <>
                <p className="mb-2 text-gray-600">Share this code with your guest:</p>
                <div className="bg-gray-100 p-4 rounded-lg text-3xl font-mono tracking-widest mb-6">
                  {roomCode}
                </div>
                <p className="text-sm text-yellow-600 animate-pulse">Waiting for guest to join...</p>
              </>
            )}

            {role === 'HOST' && roomState === 'PENDING_HOST_APPROVAL' && (
              <div className="mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                <p className="font-semibold text-yellow-800 mb-2">Guest wants to join!</p>
                <pre className="text-xs text-left bg-white p-2 rounded border mb-4 overflow-x-auto">
                  {JSON.stringify(guestMetadata, null, 2)}
                </pre>
              </div>
            )}

            {role === 'GUEST' && roomState === 'PENDING_HOST_APPROVAL' && (
              <p className="text-sm text-yellow-600 animate-pulse mt-4">
                Waiting for the Host to approve your connection...
              </p>
            )}
          </div>
        ) : (
          <div className="space-y-6">
            <div>
              <p className="mb-4 text-gray-600">Create a new room to send or receive files:</p>
              <button
                onClick={handleCreateRoom}
                disabled={isLoading}
                className="w-full py-3 px-4 rounded-lg font-bold text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 transition-colors"
              >
                {isLoading ? 'Connecting...' : 'Create New Room'}
              </button>
            </div>

            <div className="relative flex py-2 items-center">
              <div className="flex-grow border-t border-gray-200"></div>
              <span className="flex-shrink mx-4 text-gray-400 text-sm">OR</span>
              <div className="flex-grow border-t border-gray-200"></div>
            </div>

            <form onSubmit={handleJoinRoom} className="space-y-3">
              <input
                type="text"
                maxLength={6}
                placeholder="Enter 6-digit code"
                value={joinCodeInput}
                onChange={(e) => setJoinCodeInput(e.target.value)}
                className="w-full p-3 border border-gray-300 rounded-lg text-center font-mono text-xl tracking-widest focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                type="submit"
                disabled={isLoading || joinCodeInput.trim().length !== 6}
                className="w-full py-3 px-4 rounded-lg font-bold text-gray-700 bg-gray-100 hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                Join Existing Room
              </button>
            </form>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;