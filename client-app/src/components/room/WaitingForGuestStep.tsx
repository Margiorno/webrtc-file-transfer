import { useState } from 'react';
import type { RoomCode } from '../../types/Models';

interface WaitingForGuestStepProps {
  roomCode: RoomCode;
}

export function WaitingForGuestStep({ roomCode }: WaitingForGuestStepProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(roomCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="py-4 text-center space-y-6 animate-fade-in">
      <div className="space-y-1">
        <p className="text-xs font-bold uppercase tracking-widest text-stone-400">
          Room Access Code
        </p>
        <p className="text-sm text-stone-500">
          Share this 6-digit code with your peer to establish connection
        </p>
      </div>

      <button
        onClick={handleCopy}
        title="Click to copy"
        className="w-full bg-stone-50 hover:bg-stone-100 border-2 border-stone-200 py-7 px-6 rounded-3xl text-5xl font-mono font-extrabold tracking-[0.25em] text-stone-900 transition-all cursor-pointer"
      >
        {roomCode}
        <span className="block text-xs font-sans font-semibold tracking-normal text-stone-400 mt-3">
          {copied ? '✓ Copied to clipboard!' : 'Click code to copy'}
        </span>
      </button>

      <div className="inline-flex items-center gap-3 px-4 py-2 rounded-full bg-amber-50 border border-amber-200/80 text-xs font-semibold text-amber-900">
        <span className="w-2 h-2 rounded-full bg-amber-600 animate-ping"></span>
        <span>Waiting for peer to join...</span>
      </div>
    </div>
  );
}