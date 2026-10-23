import type { DeviceMetadata, JoinDecision } from '../../types/Models';

interface HostApprovalStepProps {
  role: 'HOST' | 'GUEST';
  isLoading: boolean;
  guestMetadata: DeviceMetadata | null;
  onResolveJoin: (decision: JoinDecision) => void;
}

export function HostApprovalStep({
  role,
  isLoading,
  guestMetadata,
  onResolveJoin,
}: HostApprovalStepProps) {
  if (role === 'GUEST') {
    return (
      <p className="text-sm text-yellow-600 animate-pulse py-6">
        Waiting for the Host to approve your connection...
      </p>
    );
  }

  return (
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
  );
}