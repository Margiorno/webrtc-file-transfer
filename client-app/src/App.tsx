import { LobbyView } from './components/LobbyView';
import { RoomView } from './components/RoomView';
import { useRoomSession } from './hooks/useRoomSession';

function App() {
  const session = useRoomSession();

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4">
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">WebRTC File Transfer</h1>
        <p className="text-gray-500">Direct P2P transfer bypassing the server</p>
      </header>

      <main className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
        {session.roomCode && session.role && session.roomState ? (
          <RoomView
            role={session.role}
            roomCode={session.roomCode}
            roomState={session.roomState}
            isLoading={session.isLoading}
            guestMetadata={session.guestMetadata}
            hostTwoFactorCode={session.hostTwoFactorCode}
            guestTwoFactorOptions={session.guestTwoFactorOptions}
            onResolveJoin={session.resolveJoin}
            onSubmit2FA={session.submit2FA}
            onLeaveRoom={session.leaveRoom}
          />
        ) : (
          <LobbyView
            isLoading={session.isLoading}
            onCreateRoom={session.createRoom}
            onJoinRoom={session.joinRoom}
          />
        )}
      </main>
    </div>
  );
}

export default App;