import React, { useState, useEffect } from 'react';
import { ArrowRight, Lock } from 'lucide-react';

export default function PasscodeScreen({ onUnlock }) {
  const [passcode, setPasscode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Check if server actually requires auth or session is already authenticated
  useEffect(() => {
    fetch('/api/auth/status', { credentials: 'include' })
      .then((res) => res.json())
      .then((data) => {
        if (data && (data.auth_required === false || data.is_authenticated === true)) {
          onUnlock('authenticated');
        }
      })
      .catch(() => {});
  }, [onUnlock]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const cleanPasscode = passcode.trim();
    if (!cleanPasscode) {
      setError('Please enter your vault passcode.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ passcode: cleanPasscode }),
      });

      if (res.status === 401) {
        setError('Incorrect passcode. Check SAVYOR_PASSCODE in .env.');
        return;
      }

      if (res.status === 429) {
        setError('Too many login attempts. Please wait 1 minute.');
        return;
      }

      if (!res.ok) {
        throw new Error('Server error verifying passcode.');
      }

      const data = await res.json();
      onUnlock(data.token || cleanPasscode);
    } catch (err) {
      setError(err.message || 'Failed to connect to Savyor server.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#FAFAF8] flex items-center justify-center p-4">
      <div className="w-full max-w-sm border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-8 text-center shadow-none">
        {/* Brand: Savyor Mark + Wordmark */}
        <div className="flex items-center justify-center gap-2 mb-2">
          <img src="/logo-mark.svg" alt="Savyor" className="w-5 h-5 text-[#1A1A1A]" />
          <h1 className="font-serif text-2xl font-normal tracking-tight text-[#1A1A1A] lowercase">
            savyor
          </h1>
        </div>

        {/* Tagline */}
        <p className="text-xs text-[#6B6B6B] mb-8 font-normal">
          Save the reel. Keep the idea.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="text-left">
            <label className="block text-xs font-normal text-[#6B6B6B] mb-1.5">
              Vault Passcode
            </label>
            <input
              type="password"
              placeholder="Enter passcode..."
              value={passcode}
              onChange={(e) => {
                setPasscode(e.target.value);
                if (error) setError('');
              }}
              autoFocus
              className="w-full bg-[#FAFAF8] border border-[#E8E6E1] text-[#1A1A1A] placeholder-[#6B6B6B] text-xs py-2.5 px-3 rounded-[8px] focus:outline-none focus:border-[#2F5D50] transition-colors"
            />
          </div>

          {error && (
            <p className="text-xs text-[#B3382C] text-left leading-relaxed">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading || !passcode.trim()}
            className="w-full py-2.5 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] disabled:opacity-40 text-[#FAFAF8] text-xs font-medium flex items-center justify-center gap-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-[#2F5D50]/30"
          >
            <span>{loading ? 'Unlocking...' : 'Unlock vault'}</span>
            <ArrowRight size={13} />
          </button>
        </form>
      </div>
    </div>
  );
}
