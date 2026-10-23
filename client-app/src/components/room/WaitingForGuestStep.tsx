import type { RoomCode } from '../../types/Models';

interface WaitingForGuestStepProps {
  roomCode: RoomCode;
}

export function WaitingForGuestStep({ roomCode }: WaitingForGuestStepProps) {
  return (
    <div className="py-4">
      <p className="mb-2 text-gray-600">Share this code with your guest:</p>
      <div className="bg-gray-100 p-4 rounded-lg text-3xl font-mono tracking-widest mb-4">
        {roomCode}
      </div>
      <p className="text-sm text-yellow-600 animate-pulse">Waiting for guest to join...</p>
    </div>
  );
}