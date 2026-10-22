// src/components/RoomView.tsx
import type { DeviceMetadata, JoinDecision, RoomCode, RoomState } from '../types/Models';

interface RoomViewProps {
  role: 'HOST' | 'GUEST';
  roomCode: RoomCode;
  roomState: RoomState;
  isLoading: boolean;
  guestMetadata: DeviceMetadata | null;
  hostTwoFactorCode: string | null;
  guestTwoFactorOptions: string[];
  onResolveJoin: (decision: JoinDecision) => void;
  onSubmit2FA: (selectedCode: string) => void;
  onLeaveRoom: () => void;
}

export function RoomView({
  role,
  roomCode,
  roomState,
  isLoading,
  guestMetadata,
  hostTwoFactorCode,
  guestTwoFactorOptions,
  onResolveJoin,
  onSubmit2FA,
  onLeaveRoom,
}: RoomViewProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between border-b pb-3">
        <span className="px-3 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-800">
          Role: {role}
        </span>
        <span className="px-3 py-1 text-xs font-semibold rounded-full bg-gray-100 text-gray-700">
          State: {roomState}
        </span>
      </div>

      <h2 className="text-2xl font-bold text-gray-800">Room {roomCode}</h2>

      {/* 1. WAITING FOR GUEST */}
      {role === 'HOST' && roomState === 'WAITING_FOR_GUEST' && (
        <div className="py-4">
          <p className="mb-2 text-gray-600">Share this code with your guest:</p>
          <div className="bg-gray-100 p-4 rounded-lg text-3xl font-mono tracking-widest mb-4">
            {roomCode}
          </div>
          <p className="text-sm text-yellow-600 animate-pulse">Waiting for guest to join...</p>
        </div>
      )}

      {/* 2. PENDING HOST APPROVAL */}
      {role === 'HOST' && roomState === 'PENDING_HOST_APPROVAL' && (
        <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg text-left space-y-3">
          <p className="font-bold text-yellow-900 text-center">Guest wants to join!</p>
          {guestMetadata && (
            <div className="text-sm bg-white p-3 rounded border border-yellow-100 space-y-1">
              <p><span className="font-semibold">Device:</span> {guestMetadata.deviceType ?? 'Unknown'}</p>
              <p><span className="font-semibold">OS:</span> {guestMetadata.os ?? 'Unknown'}</p>
              <p><span className="font-semibold">Browser:</span> {guestMetadata.browser ?? 'Unknown'}</p>
            </div>
          )}
          <div className="grid grid-cols-2 gap-3 pt-2">
            <button
              onClick={() => onResolveJoin('ACCEPT')}
              disabled={isLoading}
              className="py-2 px-4 rounded-lg font-bold text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
            >
              Accept
            </button>
            <button
              onClick={() => onResolveJoin('REJECT')}
              disabled={isLoading}
              className="py-2 px-4 rounded-lg font-bold text-white bg-red-600 hover:bg-red-700 disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        </div>
      )}

      {role === 'GUEST' && roomState === 'PENDING_HOST_APPROVAL' && (
        <p className="text-sm text-yellow-600 animate-pulse py-6">
          Waiting for the Host to approve your connection...
        </p>
      )}

      {/* 3. PENDING 2FA CHALLENGE */}
      {role === 'HOST' && roomState === 'PENDING_2FA' && (
        <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg space-y-2">
          <p className="text-sm text-purple-900 font-medium">
            Ask your guest to select this 2FA verification code:
          </p>
          <div className="bg-white p-4 rounded-lg text-4xl font-mono font-bold text-purple-700 tracking-widest border border-purple-100">
            {hostTwoFactorCode ?? '---'}
          </div>
          <p className="text-xs text-purple-600 animate-pulse">
            Waiting for guest to complete 2FA...
          </p>
        </div>
      )}

      {role === 'GUEST' && roomState === 'PENDING_2FA' && (
        <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg space-y-3">
          <p className="text-sm text-purple-900 font-medium">
            Select the 2FA code displayed on the Host screen:
          </p>
          <div className="grid grid-cols-3 gap-3">
            {guestTwoFactorOptions.map((option) => (
              <button
                key={option}
                onClick={() => onSubmit2FA(option)}
                disabled={isLoading}
                className="py-3 px-2 bg-white hover:bg-purple-600 hover:text-white text-purple-800 font-mono text-xl font-bold rounded-lg border border-purple-300 transition-colors disabled:opacity-50"
              >
                {option}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 4. ROOM ACTIVE */}
      {roomState === 'ACTIVE' && (
        <div className="p-6 bg-green-50 border border-green-200 rounded-lg space-y-2">
          <h3 className="text-lg font-bold text-green-800">Room is ACTIVE!</h3>
          <p className="text-sm text-green-700">
            Both peers are authenticated. Ready to establish WebRTC P2P DataChannel.
          </p>
        </div>
      )}

      {/* 5. ROOM CLOSED / REJECTED */}
      {roomState === 'CLOSED' && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg space-y-3">
          <p className="font-bold text-red-800">Room has been closed or connection was rejected.</p>
          <button
            onClick={onLeaveRoom}
            className="py-2 px-4 bg-gray-800 text-white rounded-lg text-sm font-semibold hover:bg-gray-900"
          >
            Back to Lobby
          </button>
        </div>
      )}
    </div>
  );
}