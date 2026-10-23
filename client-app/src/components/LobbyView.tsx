import { useState, type FormEvent } from 'react';
import type { SecurityMode } from '../types/Models';

interface LobbyViewProps {
  isLoading: boolean;
  onCreateRoom: (securityMode: SecurityMode) => void;
  onJoinRoom: (roomCode: string) => void;
}

export function LobbyView({
  isLoading,
  onCreateRoom,
  onJoinRoom,
}: LobbyViewProps) {
  const [securityMode, setSecurityMode] =
    useState<SecurityMode>('STANDARD');
  const [joinCodeInput, setJoinCodeInput] = useState<string>('');

  const handleJoinSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const code = joinCodeInput.trim();

    if (code.length === 6) {
      onJoinRoom(code);
    }
  };

  return (
    <div className="space-y-8 animate-fade-in">
      <div className="space-y-4">
        <label className="block text-xs font-bold uppercase tracking-widest text-stone-400">
          Security Mode
        </label>

        <div
          className="grid grid-cols-2 gap-1 p-1 bg-stone-100 rounded-2xl border border-stone-200/80"
          role="group"
          aria-label="Security Mode"
        >
          <button
            type="button"
            onClick={() => setSecurityMode('STANDARD')}
            aria-pressed={securityMode === 'STANDARD'}
            className={`min-w-0 py-3.5 px-4 rounded-xl text-sm font-bold border cursor-pointer transition-colors duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stone-500 focus-visible:ring-offset-2 ${
              securityMode === 'STANDARD'
                ? 'bg-white text-stone-900 border-stone-200/60 shadow-sm'
                : 'bg-transparent text-stone-500 border-transparent hover:text-stone-800'
            }`}
          >
            Standard
          </button>

          <button
            type="button"
            onClick={() => setSecurityMode('TWO_FACTOR')}
            aria-pressed={securityMode === 'TWO_FACTOR'}
            className={`min-w-0 py-3.5 px-4 rounded-xl text-sm font-bold border cursor-pointer transition-colors duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stone-500 focus-visible:ring-offset-2 ${
              securityMode === 'TWO_FACTOR'
                ? 'bg-white text-stone-900 border-stone-200/60 shadow-sm'
                : 'bg-transparent text-stone-500 border-transparent hover:text-stone-800'
            }`}
          >
            2FA PIN
          </button>
        </div>

        <button
          type="button"
          onClick={() => onCreateRoom(securityMode)}
          disabled={isLoading}
          className="w-full py-5 px-6 rounded-2xl text-lg font-bold text-stone-50 bg-stone-900 hover:bg-stone-800 active:scale-[0.99] disabled:bg-stone-300 transition-all shadow-lg shadow-stone-900/15 flex items-center justify-center gap-3 cursor-pointer disabled:cursor-not-allowed"
        >
          {isLoading ? (
            <>
              <span className="w-5 h-5 border-2 border-stone-50 border-t-transparent rounded-full animate-spin" />
              <span>Initializing Room...</span>
            </>
          ) : (
            <span>Create New Room</span>
          )}
        </button>
      </div>

      <div className="relative flex items-center py-1">
        <div className="grow border-t border-stone-200" />
        <span className="shrink mx-4 text-xs font-bold uppercase tracking-widest text-stone-400">
          or join existing
        </span>
        <div className="grow border-t border-stone-200" />
      </div>

      <form onSubmit={handleJoinSubmit} className="space-y-4">
        <div className="relative">
          <input
            type="text"
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            placeholder="000000"
            aria-label="Six-digit room code"
            value={joinCodeInput}
            onChange={(e) =>
              setJoinCodeInput(e.target.value.replace(/\D/g, ''))
            }
            className="w-full py-5 px-6 bg-stone-100/80 border-2 border-stone-300 rounded-2xl text-center font-mono text-3xl font-bold tracking-[0.35em] text-stone-900 placeholder:text-stone-400 focus:outline-none focus:border-stone-900 focus:bg-white transition-colors shadow-inner"
          />
        </div>

        <button
          type="submit"
          disabled={isLoading || joinCodeInput.trim().length !== 6}
          className="w-full py-5 px-6 rounded-2xl text-lg font-bold text-stone-800 bg-stone-200/90 hover:bg-stone-300 active:scale-[0.99] disabled:opacity-40 disabled:pointer-events-none transition-all cursor-pointer shadow-sm"
        >
          Connect to Room
        </button>
      </form>
    </div>
  );
}
