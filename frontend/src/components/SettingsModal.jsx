import React from 'react';
import { X, Check } from 'lucide-react';

export default function SettingsModal({
  isOpen,
  onClose,
  autoMarkReviewed,
  onToggleAutoMarkReviewed,
}) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/35">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative z-10 w-full max-w-sm bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left shadow-none">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#E8E6E1] mb-5">
          <h2 className="font-serif text-xl font-medium text-[#1A1A1A]">
            Settings
          </h2>
          <button
            onClick={onClose}
            className="p-1.5 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
            aria-label="Close settings"
          >
            <X size={16} />
          </button>
        </div>

        {/* Setting: Auto-mark as reviewed */}
        <div className="flex items-start justify-between gap-4 py-2">
          <div>
            <label
              htmlFor="auto-mark-toggle"
              className="text-xs font-medium text-[#1A1A1A] cursor-pointer block mb-1"
            >
              Auto-mark as reviewed
            </label>
            <p className="text-[11px] text-[#6B6B6B] leading-relaxed">
              Automatically mark an unread reel as reviewed after 3 seconds of viewing.
            </p>
          </div>

          <button
            id="auto-mark-toggle"
            type="button"
            role="switch"
            aria-checked={autoMarkReviewed}
            onClick={() => onToggleAutoMarkReviewed(!autoMarkReviewed)}
            className={`w-11 h-6 shrink-0 rounded-full transition-colors relative flex items-center p-0.5 focus:outline-none focus:ring-1 focus:ring-[#2F5D50] ${
              autoMarkReviewed ? 'bg-[#2F5D50]' : 'bg-[#E8E6E1]'
            }`}
          >
            <span
              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                autoMarkReviewed ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Footer */}
        <div className="pt-6 border-t border-[#E8E6E1] mt-5 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-xs font-medium transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
