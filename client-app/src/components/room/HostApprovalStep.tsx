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
      <div className="py-10 text-center space-y-4 animate-fade-in">
        <div className="w-10 h-10 border-3 border-amber-800 border-t-transparent rounded-full animate-spin mx-auto"></div>
        <div className="space-y-1">
          <p className="text-lg font-bold text-stone-900">Awaiting Host Approval</p>
          <p className="text-sm text-stone-500">
            The host is reviewing your device fingerprint...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 text-left animate-fade-in">
      <div className="space-y-1 text-center">
        <h3 className="text-xl font-extrabold text-stone-900">Incoming Peer Request</h3>
        <p className="text-sm text-stone-500">Verify device details before granting access</p>
      </div>

      {guestMetadata && (
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-stone-50 p-4 rounded-2xl border border-stone-200 text-center">
            <span className="block text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-1">
              Device
            </span>
            <span className="font-mono text-sm font-bold text-stone-900">
              {guestMetadata.deviceType ?? 'Unknown'}
            </span>
          </div>
          <div className="bg-stone-50 p-4 rounded-2xl border border-stone-200 text-center">
            <span className="block text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-1">
              System
            </span>
            <span className="font-mono text-sm font-bold text-stone-900">
              {guestMetadata.os ?? 'Unknown'}
            </span>
          </div>
          <div className="bg-stone-50 p-4 rounded-2xl border border-stone-200 text-center">
            <span className="block text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-1">
              Browser
            </span>
            <span className="font-mono text-sm font-bold text-stone-900">
              {guestMetadata.browser ?? 'Unknown'}
            </span>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 pt-2">
        <button
          onClick={() => onResolveJoin('REJECT')}
          disabled={isLoading}
          className="py-5 px-6 rounded-2xl text-base font-bold text-stone-700 bg-stone-100 hover:bg-red-50 hover:text-red-700 hover:border-red-200 border-2 border-transparent disabled:opacity-50 transition-all cursor-pointer"
        >
          Decline
        </button>
        <button
          onClick={() => onResolveJoin('ACCEPT')}
          disabled={isLoading}
          className="py-5 px-6 rounded-2xl text-base font-bold text-stone-50 bg-stone-900 hover:bg-stone-800 disabled:opacity-50 transition-all shadow-lg shadow-stone-900/15 cursor-pointer"
        >
          {isLoading ? 'Processing...' : 'Approve Peer'}
        </button>
      </div>
    </div>
  );
}