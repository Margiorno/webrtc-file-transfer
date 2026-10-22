import { useState } from "react";
import { roomApiService } from "./services/RoomApiService";
import { signalingService } from "./services/SignalingService";



function App() {
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [roomCode, setRoomCode] = useState<string | null>(null);

    const handleCreateRoom = async () => {
      try {
        setIsLoading(true);

        const response = await roomApiService.createRoom()
        console.log("Room created:", response);

        await signalingService.connect(response.roomCode, response.hostId, response.hostSecret);

        setRoomCode(response.roomCode);
      } catch (error) {
        console.error("Failed to create room:", error);
        alert('Error connecting to the server')
      } finally {
        setIsLoading(false);
      }
    };

    return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4">
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">WebRTC File Transfer</h1>
        <p className="text-gray-500">Direct P2P transfer bypassing the server</p>
      </header>

      <main className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
        {}
        {roomCode ? (
          <div>
            <h2 className="text-2xl font-bold text-green-600 mb-4">Room Created!</h2>
            <p className="mb-2 text-gray-600">Share this code with your guest:</p>
            <div className="bg-gray-100 p-4 rounded-lg text-3xl font-mono tracking-widest mb-6">
              {roomCode}
            </div>
            <p className="text-sm text-yellow-600 animate-pulse">Waiting for guest to join...</p>
          </div>
        ) : (
          <div>
            <p className="mb-6 text-gray-600">Start by creating a new secure transfer room.</p>
            <button 
              onClick={handleCreateRoom}
              disabled={isLoading}
              className={`w-full py-3 px-4 rounded-lg font-bold text-white transition-colors
                ${isLoading ? 'bg-blue-300 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'}`}
            >
              {isLoading ? 'Connecting...' : 'Create New Room'}
            </button>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;