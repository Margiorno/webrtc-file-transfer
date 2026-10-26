interface TwoFactorStepProps {
  role: 'HOST' | 'GUEST';
  isLoading: boolean;
  hostTwoFactorCode: string | null;
  guestTwoFactorOptions: string[];
  onSubmit2FA: (selectedCode: string) => void;
}

export function TwoFactorStep({
  role,
  isLoading,
  hostTwoFactorCode,
  guestTwoFactorOptions,
  onSubmit2FA,
}: TwoFactorStepProps) {
  if (role === 'HOST') {
    return (
      <div className="py-4 text-center space-y-6 animate-fade-in">
        <div className="space-y-1">
          <p className="text-xs font-bold uppercase tracking-widest text-stone-400">
            2FA Verification PIN
          </p>
          <p className="text-sm text-stone-500">
            Ask your guest to select this exact code on their screen
          </p>
        </div>

        <div className="bg-stone-900 text-stone-50 py-8 px-6 rounded-3xl text-5xl font-mono font-extrabold tracking-[0.3em] shadow-lg">
          {hostTwoFactorCode ?? '---'}
        </div>

        <p className="text-xs font-semibold text-amber-800 animate-pulse">
          Waiting for guest to verify PIN...
        </p>
      </div>
    );
  }

  return (
    <div className="py-4 text-center space-y-6 animate-fade-in">
      <div className="space-y-1">
        <p className="text-xs font-bold uppercase tracking-widest text-stone-400">
          Security Challenge
        </p>
        <p className="text-sm text-stone-500">
          Tap the 3-digit PIN displayed on the Host screen
        </p>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {guestTwoFactorOptions.map((option) => (
          <button
            key={option}
            onClick={() => onSubmit2FA(option)}
            disabled={isLoading}
            className="py-6 px-3 bg-stone-50 hover:bg-stone-900 hover:text-stone-50 text-stone-900 font-mono text-2xl font-extrabold rounded-2xl border-2 border-stone-200 hover:border-stone-900 transition-all active:scale-95 disabled:opacity-40 cursor-pointer"
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  );
}