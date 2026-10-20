import React from 'react';

function App() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4">
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">WebRTC File Transfer</h1>
        <p className="text-gray-500">Bezpośredni transfer P2P omijający serwer</p>
      </header>

      <main className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full text-center">
        <p className="mb-4 font-medium">Inicjalizacja aplikacji...</p>
        <div className="text-sm text-gray-400">
          Status połączenia: <span className="text-yellow-500">Oczekiwanie</span>
        </div>
      </main>
    </div>
  );
}

export default App;