import React, { useState, useEffect, useRef } from 'react';
import { X, ArrowRight, Clipboard, Upload, FileVideo, CheckCircle2 } from 'lucide-react';

export function extractInstagramUrl(input) {
  if (!input) return '';
  const match = input.match(/https?:\/\/(?:www\.)?instagram\.com\/(?:reel|reels|p|share\/reel)\/[A-Za-z0-9_-]+[^\s]*/);
  return match ? match[0] : input.trim();
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B';
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function SaveReelModal({ isOpen, onClose, onSave, onUpload, initialUrl = '' }) {
  const [tab, setTab] = useState('link'); // 'link' | 'upload'
  const [url, setUrl] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const fileInputRef = useRef(null);

  useEffect(() => {
    if (initialUrl) {
      setUrl(extractInstagramUrl(initialUrl));
      setTab('link');
    }
  }, [initialUrl, isOpen]);

  useEffect(() => {
    if (!isOpen) {
      setUrl('');
      setSelectedFile(null);
      setError('');
      setSubmitting(false);
      setUploadProgress(0);
      setTab('link');
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) setUrl(text);
    } catch (err) {
      console.error("Clipboard access failed:", err);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setError('');
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
    if (!['.mp4', '.mov'].includes(ext)) {
      setError('Invalid file format. Only .mp4 and .mov files are supported.');
      setSelectedFile(null);
      return;
    }

    if (file.size > 100 * 1024 * 1024) {
      setError('File exceeds maximum size of 100 MB.');
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (submitting) return;

    const file = e.dataTransfer.files?.[0];
    if (!file) return;

    setError('');
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
    if (!['.mp4', '.mov'].includes(ext)) {
      setError('Invalid file format. Only .mp4 and .mov files are supported.');
      setSelectedFile(null);
      return;
    }

    if (file.size > 100 * 1024 * 1024) {
      setError('File exceeds maximum size of 100 MB.');
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleLinkSubmit = async (e) => {
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

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!selectedFile) {
      setError('Please choose an MP4 or MOV video file to upload.');
      return;
    }

    setSubmitting(true);
    setError('');
    setUploadProgress(20);

    try {
      if (onUpload) {
        setUploadProgress(50);
        await onUpload(selectedFile);
      } else {
        // Fallback direct upload if onUpload prop isn't passed
        const formData = new FormData();
        formData.append('file', selectedFile);
        
        setUploadProgress(40);
        const res = await fetch('/api/reels/upload', {
          method: 'POST',
          credentials: 'include',
          body: formData,
        });

        if (res.status === 401) {
          throw new Error('Vault is locked. Please unlock first.');
        }
        if (res.status === 413) {
          throw new Error('Video file exceeds 100 MB limit.');
        }
        if (!res.ok) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.detail || 'Failed to upload video.');
        }
      }

      setUploadProgress(100);
      setSelectedFile(null);
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to upload video. Please try again.');
    } finally {
      setSubmitting(false);
      setUploadProgress(0);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/30">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative z-10 w-full max-w-md bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left shadow-lg">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-serif text-lg font-medium text-[#1A1A1A]">
            {tab === 'upload' ? 'Upload video' : 'Add Instagram reel'}
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <p className="text-xs text-[#6B6B6B] mb-4">
          Save the video and extract structured reading notes.
        </p>

        {/* Option Tabs */}
        <div className="flex gap-1.5 p-1 mb-5 bg-[#FAFAF8] rounded-[8px] border border-[#E8E6E1]">
          <button
            type="button"
            onClick={() => {
              setTab('link');
              setError('');
            }}
            className={`flex-1 py-1.5 text-xs font-medium rounded-[6px] transition-all ${
              tab === 'link'
                ? 'bg-white text-[#1A1A1A] shadow-xs border border-[#E8E6E1]/50'
                : 'text-[#6B6B6B] hover:text-[#1A1A1A]'
            }`}
          >
            Instagram link
          </button>
          <button
            type="button"
            onClick={() => {
              setTab('upload');
              setError('');
            }}
            className={`flex-1 py-1.5 text-xs font-medium rounded-[6px] transition-all flex items-center justify-center gap-1.5 ${
              tab === 'upload'
                ? 'bg-white text-[#1A1A1A] shadow-xs border border-[#E8E6E1]/50'
                : 'text-[#6B6B6B] hover:text-[#1A1A1A]'
            }`}
          >
            <Upload size={13} />
            <span>Upload video</span>
          </button>
        </div>

        {tab === 'link' ? (
          /* Link Form */
          <form onSubmit={handleLinkSubmit} className="space-y-4">
            <div className="space-y-1.5">
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
              <div className="flex items-center justify-between px-1 text-[11px] text-[#6B6B6B]">
                <span>Instagram /reel/ or /p/ link</span>
                <button
                  type="button"
                  onClick={() => {
                    setTab('upload');
                    setError('');
                  }}
                  className="text-[#2F5D50] hover:underline font-medium flex items-center gap-1"
                >
                  <Upload size={11} />
                  <span>Or upload video</span>
                </button>
              </div>
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
        ) : (
          /* Upload Form */
          <form onSubmit={handleUploadSubmit} className="space-y-4">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept=".mp4,.mov,video/mp4,video/quicktime"
              className="hidden"
            />

            {!selectedFile ? (
              <div
                onClick={() => fileInputRef.current?.click()}
                onDrop={handleDrop}
                onDragOver={handleDragOver}
                className="border-2 border-dashed border-[#E8E6E1] hover:border-[#1A1A1A]/40 rounded-[10px] p-6 bg-[#FAFAF8] text-center cursor-pointer transition-colors"
              >
                <div className="w-10 h-10 rounded-full bg-white border border-[#E8E6E1] flex items-center justify-center mx-auto mb-2 text-[#2F5D50]">
                  <Upload size={18} />
                </div>
                <p className="text-xs font-medium text-[#1A1A1A] mb-1">
                  Click to select video or drag & drop
                </p>
                <p className="text-[11px] text-[#6B6B6B]">
                  MP4 or MOV up to 100 MB
                </p>
              </div>
            ) : (
              <div className="border border-[#E8E6E1] rounded-[10px] p-3.5 bg-[#FAFAF8] flex items-center justify-between">
                <div className="flex items-center gap-3 overflow-hidden">
                  <div className="w-9 h-9 rounded-[8px] bg-white border border-[#E8E6E1] flex items-center justify-center text-[#2F5D50] shrink-0">
                    <FileVideo size={18} />
                  </div>
                  <div className="overflow-hidden">
                    <p className="text-xs font-medium text-[#1A1A1A] truncate">
                      {selectedFile.name}
                    </p>
                    <p className="text-[11px] text-[#6B6B6B]">
                      {formatFileSize(selectedFile.size)}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = '';
                  }}
                  disabled={submitting}
                  className="p-1 rounded-[6px] text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-white transition-colors"
                  title="Remove selected file"
                >
                  <X size={15} />
                </button>
              </div>
            )}

            {submitting && (
              <div className="space-y-1.5 pt-1">
                <div className="flex justify-between text-[11px] text-[#6B6B6B]">
                  <span>Uploading video...</span>
                  <span>{uploadProgress > 0 ? `${uploadProgress}%` : ''}</span>
                </div>
                <div className="w-full bg-[#E8E6E1] h-1.5 rounded-full overflow-hidden">
                  <div
                    className="bg-[#2F5D50] h-full transition-all duration-300 rounded-full"
                    style={{ width: `${Math.max(uploadProgress, 25)}%` }}
                  />
                </div>
              </div>
            )}

            {error && (
              <p className="text-xs text-red-600 leading-relaxed">
                {error}
              </p>
            )}

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="px-3.5 py-2 rounded-[8px] border border-[#E8E6E1] text-[#1A1A1A] hover:bg-[#FAFAF8] text-xs font-normal transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting || !selectedFile}
                className="px-4 py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] disabled:opacity-40 text-[#FAFAF8] text-xs font-medium flex items-center gap-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-[#2F5D50]/30"
              >
                <span>{submitting ? 'Uploading...' : 'Upload & save'}</span>
                <ArrowRight size={13} />
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}