import React, { useState, useEffect } from 'react';
import { X, ArrowRight, Clipboard } from 'lucide-react';

export function extractInstagramUrl(input) {
  if (!input) return '';
  const match = input.match(/https?:\/\/(?:www\.)?instagram\.com\/(?:reel|reels|p|share\/reel)\/[A-Za-z0-9_-]+[^\s]*/);
  return match ? match[0] : input.trim();
}

export default function SaveReelModal({ isOpen, onClose, onSave, initialUrl = '' }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (initialUrl) {
      setUrl(extractInstagramUrl(initialUrl));
    }
  }, [initialUrl, isOpen]);

  if (!isOpen) return null;

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) setUrl(text);
    } catch (err) {
      console.error("Clipboard access failed:", err);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const cleanUrl = url.trim();
    if (!cleanUrl) return;
    if (!cleanUrl.includes('instagram.com/')) {
      setError('Please provide a valid Instagram Reel link (e.g. https://www.instagram.com/reel/...)');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      await onSave(cleanUrl);
      setUrl('');
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to queue reel. Please check URL and try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/30">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative z-10 w-full max-w-md bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-serif text-lg font-medium text-[#1A1A1A]">
            Add Instagram reel
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <p className="text-xs text-[#6B6B6B] mb-5">
          Save the reel and extract structured reading notes.
        </p>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="relative">
            <input
              type="url"
              placeholder="https://www.instagram.com/reel/..."
              value={url}
              onChange={(e) => {
                setUrl(e.target.value);
                if (error) setError('');
              }}
              disabled={submitting}
              autoFocus
              className="w-full bg-[#FAFAF8] border border-[#E8E6E1] text-[#1A1A1A] placeholder-[#6B6B6B] text-xs py-2.5 pl-3 pr-16 rounded-[8px] focus:outline-none focus:border-[#1A1A1A] transition-colors"
            />
            <button
              type="button"
              onClick={handlePaste}
              disabled={submitting}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-[#6B6B6B] hover:text-[#1A1A1A] px-2 py-1 rounded font-normal"
            >
              Paste
            </button>
          </div>

          {error && (
            <p className="text-xs text-red-600 leading-relaxed">
              {error}
            </p>
          )}

          <div className="flex items-center justify-end gap-2.5 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 rounded-[8px] border border-[#E8E6E1] text-[#1A1A1A] hover:bg-[#FAFAF8] text-xs font-normal transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !url.trim()}
              className="px-4 py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] disabled:opacity-40 text-[#FAFAF8] text-xs font-medium flex items-center gap-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-[#2F5D50]/30"
            >
              <span>{submitting ? 'Saving...' : 'Save reel'}</span>
              <ArrowRight size={13} />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
