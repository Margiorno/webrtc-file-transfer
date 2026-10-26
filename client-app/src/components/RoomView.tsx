import type { DeviceMetadata, JoinDecision, RoomCode, RoomState } from '../types/Models';
import { WaitingForGuestStep } from './room/WaitingForGuestStep';
import { HostApprovalStep } from './room/HostApprovalStep';
import { TwoFactorStep } from './room/TwoFactorStep';
import { RoomStatusBanner } from './room/RoomStatusBanner';

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
  const renderStepContent = () => {
    switch (roomState) {
      case 'WAITING_FOR_GUEST':
        return <WaitingForGuestStep roomCode={roomCode} />;
      case 'PENDING_HOST_APPROVAL':
        return (
          <HostApprovalStep
            role={role}
            isLoading={isLoading}
            guestMetadata={guestMetadata}
            onResolveJoin={onResolveJoin}
          />
        );
      case 'PENDING_2FA':
        return (
          <TwoFactorStep
            role={role}
            isLoading={isLoading}
            hostTwoFactorCode={hostTwoFactorCode}
            guestTwoFactorOptions={guestTwoFactorOptions}
            onSubmit2FA={onSubmit2FA}
          />
        );
      case 'ACTIVE':
      case 'CLOSED':
        return <RoomStatusBanner state={roomState} onLeaveRoom={onLeaveRoom} />;
    }
  };

  const isHost = role === 'HOST';

  return (
    <div className="space-y-8 animate-fade-in">
      <div className="flex items-center justify-between border-b border-stone-200 pb-5">
        <div className="flex items-center gap-3">
          <span
            className={`px-3.5 py-1.5 text-xs font-bold tracking-widest uppercase rounded-xl ${
              isHost
                ? 'bg-stone-900 text-stone-50'
                : 'bg-amber-900 text-amber-50'
            }`}
          >
            {isHost ? 'Host Session' : 'Guest Session'}
          </span>
          <span className="text-sm font-mono font-bold text-stone-400">#{roomCode}</span>
        </div>

        <button
          onClick={onLeaveRoom}
          className="text-xs font-bold uppercase tracking-wider text-stone-400 hover:text-stone-900 transition-colors cursor-pointer"
        >
          Leave
        </button>
      </div>

      {renderStepContent()}
    </div>
  );
}