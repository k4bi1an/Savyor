import React, { useState, useRef } from 'react';
import { X, Download, Upload, FileText, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function SettingsModal({
  isOpen,
  onClose,
  autoMarkReviewed,
  onToggleAutoMarkReviewed,
  reels = [],
  onReprocessAll,
  onRefreshReels,
}) {
  const [reprocessing, setReprocessing] = useState(false);
  const [reprocessProgress, setReprocessProgress] = useState({ current: 0, total: 0 });
  const [cancelReprocess, setCancelReprocess] = useState(false);
  const [restoreStatus, setRestoreStatus] = useState(null);
  const [isRestoring, setIsRestoring] = useState(false);
  const fileInputRef = useRef(null);

  if (!isOpen) return null;

  const handleDownloadBackup = () => {
    window.location.href = '/api/backup/download';
  };

  const handleExportMarkdown = () => {
    window.location.href = '/api/export/markdown';
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!confirm('Restoring this backup will replace current database and media. An automatic safety backup will be created first. Continue?')) {
      e.target.value = '';
      return;
    }

    setIsRestoring(true);
    setRestoreStatus(null);
    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await fetch('/api/backup/restore', {
        method: 'POST',
        credentials: 'include',
        body: formData,
      });

      const data = await res.json();
      if (res.ok) {
        setRestoreStatus({ type: 'success', msg: data.message || 'Restored successfully!' });
        if (onRefreshReels) onRefreshReels();
      } else {
        setRestoreStatus({ type: 'error', msg: data.detail || 'Restore failed.' });
      }
    } catch (err) {
      setRestoreStatus({ type: 'error', msg: 'Network or upload error during restore.' });
    } finally {
      setIsRestoring(false);
      e.target.value = '';
    }
  };

  const handleStartReprocessAll = async () => {
    if (!confirm(`Reprocess all ${reels.length} reels with Gemini? User edits (tags, categories, status) will be preserved.`)) {
      return;
    }

    setReprocessing(true);
    setCancelReprocess(false);
    setReprocessProgress({ current: 0, total: reels.length });

    for (let i = 0; i < reels.length; i++) {
      if (cancelReprocess) break;
      setReprocessProgress({ current: i + 1, total: reels.length });
      const r = reels[i];
      try {
        await fetch(`/api/reels/${r.id}/reprocess`, {
          method: 'POST',
          credentials: 'include',
        });
      } catch (err) {
        console.error(`Failed to trigger reprocess for ${r.id}:`, err);
      }
      // Brief pause between queueing
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    setReprocessing(false);
    if (onRefreshReels) onRefreshReels();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/35">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative z-10 w-full max-w-md bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left shadow-none max-h-[90vh] overflow-y-auto">
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
        <div className="flex items-start justify-between gap-4 py-2 border-b border-[#E8E6E1] pb-4">
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

        {/* Reprocess All Section */}
        <div className="py-4 border-b border-[#E8E6E1] space-y-2">
          <h3 className="text-xs font-medium text-[#1A1A1A]">
            Reprocess Notes
          </h3>
          <p className="text-[11px] text-[#6B6B6B] leading-relaxed">
            Re-run Gemini extraction on local videos to update to latest note types, key moments, and timestamps without overwriting your manual edits.
          </p>

          {reprocessing ? (
            <div className="space-y-2 pt-1">
              <div className="flex items-center justify-between text-xs">
                <span className="text-[#2F5D50] font-medium">
                  Reprocessing {reprocessProgress.current} of {reprocessProgress.total}...
                </span>
                <button
                  onClick={() => setCancelReprocess(true)}
                  className="text-[#B3382C] text-xs hover:underline"
                >
                  Cancel
                </button>
              </div>
              <div className="w-full bg-[#E8E6E1] h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#2F5D50] h-full transition-all duration-300"
                  style={{
                    width: `${reprocessProgress.total > 0 ? (reprocessProgress.current / reprocessProgress.total) * 100 : 0}%`,
                  }}
                />
              </div>
            </div>
          ) : (
            <button
              onClick={handleStartReprocessAll}
              disabled={reels.length === 0}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[6px] border border-[#E8E6E1] text-xs text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
            >
              <RefreshCw size={12} className="text-[#6B6B6B]" />
              <span>Reprocess all reels ({reels.length})</span>
            </button>
          )}
        </div>

        {/* Data & Backup Section */}
        <div className="py-4 space-y-3">
          <h3 className="text-xs font-medium text-[#1A1A1A]">
            Data & Backup
          </h3>

          <div className="flex flex-col gap-2">
            {/* Download Backup */}
            <button
              onClick={handleDownloadBackup}
              className="w-full flex items-center justify-between px-3 py-2 rounded-[6px] border border-[#E8E6E1] text-xs text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
            >
              <div className="flex items-center gap-2">
                <Download size={13} className="text-[#6B6B6B]" />
                <span>Download full backup (ZIP)</span>
              </div>
              <span className="text-[10px] text-[#6B6B6B]">Database + Media</span>
            </button>

            {/* Restore from Backup */}
            <input
              ref={fileInputRef}
              type="file"
              accept=".zip"
              className="hidden"
              onChange={handleFileChange}
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={isRestoring}
              className="w-full flex items-center justify-between px-3 py-2 rounded-[6px] border border-[#E8E6E1] text-xs text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
            >
              <div className="flex items-center gap-2">
                <Upload size={13} className="text-[#6B6B6B]" />
                <span>Restore from backup</span>
              </div>
              <span className="text-[10px] text-[#6B6B6B]">
                {isRestoring ? 'Restoring...' : 'Upload ZIP'}
              </span>
            </button>

            {/* Export Markdown */}
            <button
              onClick={handleExportMarkdown}
              className="w-full flex items-center justify-between px-3 py-2 rounded-[6px] border border-[#E8E6E1] text-xs text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
            >
              <div className="flex items-center gap-2">
                <FileText size={13} className="text-[#6B6B6B]" />
                <span>Export notes as Markdown</span>
              </div>
              <span className="text-[10px] text-[#6B6B6B]">Formatted notes ZIP</span>
            </button>
          </div>

          {/* Restore status notification */}
          {restoreStatus && (
            <div
              className={`p-2.5 rounded-[6px] text-xs flex items-center gap-2 ${
                restoreStatus.type === 'success'
                  ? 'bg-[#E4ECE8] text-[#2F5D50]'
                  : 'bg-[#FAF0EE] text-[#B3382C]'
              }`}
            >
              {restoreStatus.type === 'success' ? (
                <CheckCircle2 size={14} className="shrink-0" />
              ) : (
                <AlertCircle size={14} className="shrink-0" />
              )}
              <span>{restoreStatus.msg}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-[#E8E6E1] flex justify-end">
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
