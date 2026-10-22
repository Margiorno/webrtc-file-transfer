// src/components/LobbyView.tsx
import { useState, type FormEvent } from 'react';
import type { SecurityMode } from '../types/Models';

interface LobbyViewProps {
  isLoading: boolean;
  onCreateRoom: (securityMode: SecurityMode) => void;
  onJoinRoom: (roomCode: string) => void;
}

export function LobbyView({ isLoading, onCreateRoom, onJoinRoom }: LobbyViewProps) {
  const [securityMode, setSecurityMode] = useState<SecurityMode>('STANDARD');
  const [joinCodeInput, setJoinCodeInput] = useState<string>('');

  const handleJoinSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (joinCodeInput.trim().length === 6) {
      onJoinRoom(joinCodeInput.trim());
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <p className="mb-3 text-gray-600 font-medium">1. Select Room Security Mode:</p>
        <div className="grid grid-cols-2 gap-2 mb-4">
          <button
            type="button"
            onClick={() => setSecurityMode('STANDARD')}
            className={`py-2 px-3 rounded-lg text-sm font-semibold border transition-colors ${
              securityMode === 'STANDARD'
                ? 'bg-blue-50 border-blue-600 text-blue-700'
                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            Standard
          </button>
          <button
            type="button"
            onClick={() => setSecurityMode('TWO_FACTOR')}
            className={`py-2 px-3 rounded-lg text-sm font-semibold border transition-colors ${
              securityMode === 'TWO_FACTOR'
                ? 'bg-blue-50 border-blue-600 text-blue-700'
                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            2FA Challenge
          </button>
        </div>

        <button
          onClick={() => onCreateRoom(securityMode)}
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

      <form onSubmit={handleJoinSubmit} className="space-y-3">
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
  );
}