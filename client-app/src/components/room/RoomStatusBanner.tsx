interface RoomStatusBannerProps {
  state: 'ACTIVE' | 'CLOSED';
  onLeaveRoom: () => void;
}

export function RoomStatusBanner({ state, onLeaveRoom }: RoomStatusBannerProps) {
  if (state === 'ACTIVE') {
    return (
      <div className="p-6 bg-green-50 border border-green-200 rounded-lg space-y-2">
        <h3 className="text-lg font-bold text-green-800">Room is ACTIVE!</h3>
        <p className="text-sm text-green-700">
          Both peers are authenticated. Ready to establish WebRTC P2P DataChannel.
        </p>
      </div>
    );
  }

  return (
    <div className="p-4 bg-red-50 border border-red-200 rounded-lg space-y-3">
      <p className="font-bold text-red-800">Room has been closed or connection was rejected.</p>
      <button
        onClick={onLeaveRoom}
        className="py-2 px-4 bg-gray-800 text-white rounded-lg text-sm font-semibold hover:bg-gray-900"
      >
        Back to Lobby
      </button>
    </div>
  );
}