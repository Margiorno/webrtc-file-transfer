import { LobbyView } from './components/LobbyView';
import { RoomView } from './components/RoomView';
import { useRoomSession } from './hooks/useRoomSession';

function App() {
  const session = useRoomSession();

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-stone-100 text-stone-900 selection:bg-stone-900 selection:text-stone-50">
      <header className="mb-10 text-center space-y-3 animate-fade-in">
        <div className="inline-flex items-center gap-2.5 px-4 py-1.5 rounded-full bg-white border border-stone-200 shadow-2xs text-stone-700 text-xs font-semibold tracking-widest uppercase">
          <span className="w-2 h-2 rounded-full bg-amber-700 animate-pulse"></span>
          P2P Encrypted Tunnel
        </div>
        <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight text-stone-900">
          WebRTC Transfer
        </h1>
        <p className="text-base text-stone-500">
          Zero-knowledge browser-to-browser file sharing
        </p>
      </header>

      <main className="bg-white p-8 sm:p-10 rounded-3xl border border-stone-200/90 shadow-xl shadow-stone-900/5 max-w-lg w-full transition-all duration-300">
        {session.error && (
          <div className="mb-6 p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 flex items-start justify-between gap-3 animate-fade-in">
            <div className="flex items-center gap-3 text-sm font-medium">
              <svg className="w-5 h-5 text-red-600 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>{session.error}</span>
            </div>
            <button
              onClick={session.clearError}
              className="text-red-500 hover:text-red-800 p-1 rounded-lg transition-colors"
              aria-label="Dismiss error"
            >
              ✕
            </button>
          </div>
        )}

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