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
      <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg space-y-2">
        <p className="text-sm text-purple-900 font-medium">
          Ask your guest to select this 2FA verification code:
        </p>
        <div className="bg-white p-4 rounded-lg text-4xl font-mono font-bold text-purple-700 tracking-widest border border-purple-100">
          {hostTwoFactorCode ?? '---'}
        </div>
        <p className="text-xs text-purple-600 animate-pulse">
          Waiting for guest to complete 2FA...
        </p>
      </div>
    );
  }

  return (
    <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg space-y-3">
      <p className="text-sm text-purple-900 font-medium">
        Select the 2FA code displayed on the Host screen:
      </p>
      <div className="grid grid-cols-3 gap-3">
        {guestTwoFactorOptions.map((option) => (
          <button
            key={option}
            onClick={() => onSubmit2FA(option)}
            disabled={isLoading}
            className="py-3 px-2 bg-white hover:bg-purple-600 hover:text-white text-purple-800 font-mono text-xl font-bold rounded-lg border border-purple-300 transition-colors disabled:opacity-50"
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  );
}