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

      {renderStepContent()}
    </div>
  );
}