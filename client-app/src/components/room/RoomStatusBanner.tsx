interface RoomStatusBannerProps {
  state: 'ACTIVE' | 'CLOSED';
  onLeaveRoom: () => void;
}

export function RoomStatusBanner({ state, onLeaveRoom }: RoomStatusBannerProps) {
  if (state === 'ACTIVE') {
    return (
      <div className="py-8 text-center space-y-5 animate-fade-in">
        <div className="w-14 h-14 rounded-2xl bg-emerald-100 text-emerald-800 flex items-center justify-center mx-auto text-2xl font-bold">
          ✓
        </div>
        <div className="space-y-1.5">
          <h3 className="text-2xl font-extrabold text-stone-900">Tunnel Active</h3>
          <p className="text-sm text-stone-500 max-w-xs mx-auto leading-relaxed">
            Both peers are verified. Ready for direct P2P WebRTC DataChannel streaming.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="py-6 text-center space-y-6 animate-fade-in">
      <div className="w-14 h-14 rounded-2xl bg-red-100 text-red-700 flex items-center justify-center mx-auto text-2xl font-bold">
        ✕
      </div>
      <div className="space-y-1">
        <p className="text-xl font-extrabold text-stone-900">Session Closed</p>
        <p className="text-sm text-stone-500">
          The room has been terminated or the connection was declined.
        </p>
      </div>
      <button
        onClick={onLeaveRoom}
        className="w-full py-5 px-6 bg-stone-900 text-stone-50 rounded-2xl text-base font-bold hover:bg-stone-800 transition-all cursor-pointer"
      >
        Return to Lobby
      </button>
    </div>
  );
}