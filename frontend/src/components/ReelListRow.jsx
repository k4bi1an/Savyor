import React, { useState } from 'react';
import { Bookmark, Play } from 'lucide-react';
import RowQuickActions from './RowQuickActions';

// Extract a search snippet from transcript or structured content if query did not match title or summary
export function getSearchSnippet(reel, query) {
  if (!query || !query.trim()) return null;
  const q = query.trim().toLowerCase();
  const inTitle = reel.title?.toLowerCase().includes(q);
  const inSummary = reel.summary?.toLowerCase().includes(q);
  if (inTitle || inSummary) return null;

  let text = '';
  let label = '';
  if (reel.transcript?.toLowerCase().includes(q)) {
    text = reel.transcript;
    label = 'Transcript';
  } else if (reel.structured_content?.items_or_markdown?.toLowerCase().includes(q)) {
    text = reel.structured_content.items_or_markdown;
    label = 'Notes';
  } else if ((reel.key_takeaways || []).some((t) => t.toLowerCase().includes(q))) {
    text = (reel.key_takeaways || []).find((t) => t.toLowerCase().includes(q)) || '';
    label = 'Takeaway';
  }

  if (!text) return null;

  const idx = text.toLowerCase().indexOf(q);
  const start = Math.max(0, idx - 40);
  const end = Math.min(text.length, idx + q.length + 50);

  return {
    label,
    before: (start > 0 ? '…' : '') + text.slice(start, idx),
    match: text.slice(idx, idx + q.length),
    after: text.slice(idx + q.length, end) + (end < text.length ? '…' : ''),
  };
}

export default function ReelListRow({
  reel,
  onClick,
  isFocused = false,
  searchQuery = '',
  categories = [],
  onUpdateStatus,
  onUpdateCategory,
  onDelete,
}) {
  const [imgError, setImgError] = useState(false);
  const media = reel.media || {};
  const creator = media.channel || media.uploader || 'instagram';
  const status = reel.status || 'unread';

  const activeToken = typeof window !== 'undefined' ? (localStorage.getItem('savyor_passcode') || localStorage.getItem('reelvault_token') || '') : '';
  const proxyThumb = media.proxy_thumb_url
    ? `${media.proxy_thumb_url}${activeToken ? `${media.proxy_thumb_url.includes('?') ? '&' : '?'}token=${encodeURIComponent(activeToken)}` : ''}`
    : '';

  const thumbSrc = imgError
    ? (media.thumbnail || '')
    : (proxyThumb || media.thumbnail || '');

  const savedDate = reel.created_at
    ? new Date(reel.created_at).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
      })
    : '';

  const snippet = getSearchSnippet(reel, searchQuery);

  return (
    <div
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onClick();
      }}
      className={`group relative flex items-start gap-3 sm:gap-4 py-4 px-2 sm:px-3 border-b border-[#E8E6E1] text-left cursor-pointer transition-colors hover:bg-[#F6F5F2] ${
        isFocused ? 'ring-1 ring-[#2F5D50] bg-[#F6F5F2] rounded-[8px]' : ''
      }`}
    >
      {/* 9:16 Small Thumbnail (~56x100px, rounded 8px) */}
      <div
        className="w-[56px] h-[100px] shrink-0 relative overflow-hidden rounded-[8px] border border-[#E8E6E1] bg-[#F2F0EB]"
      >
        {thumbSrc ? (
          <img
            src={thumbSrc}
            alt={reel.title}
            onError={() => {
              if (!imgError && media.thumbnail) setImgError(true);
            }}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-[#6B6B6B]">
            <Bookmark size={18} className="opacity-40" />
          </div>
        )}

        {/* Small Play Icon on Hover */}
        <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-black/25">
          <Play size={14} className="fill-[#FAFAF8] text-[#FAFAF8]" />
        </div>
      </div>

      {/* Details (using full available width) */}
      <div className="flex-1 min-w-0">
        {/* Title: Serif, max 2 lines, full width */}
        <h3 className="font-serif text-[15px] sm:text-[16px] text-[#1A1A1A] font-normal leading-snug line-clamp-2 w-full">
          {reel.title}
        </h3>

        {/* 1-Line Summary Clamped */}
        {reel.summary && (
          <p className="text-xs text-[#6B6B6B] line-clamp-1 leading-relaxed mt-1 font-normal">
            {reel.summary}
          </p>
        )}

        {/* Search Snippet (Only shown when query matches transcript/notes and not title/summary) */}
        {snippet && (
          <p className="text-[11px] text-[#1A1A1A] bg-[#EFECE6]/80 px-2 py-0.5 rounded-[4px] mt-1.5 inline-block font-mono max-w-full truncate">
            <span className="text-[#6B6B6B] font-sans mr-1">{snippet.label}:</span>
            <span>{snippet.before}</span>
            <mark className="bg-[#2F5D50]/20 text-[#2F5D50] font-semibold px-0.5 rounded">
              {snippet.match}
            </mark>
            <span>{snippet.after}</span>
          </p>
        )}

        {/* Meta Line: Separators bound to items with whitespace-nowrap so a dot never stands alone */}
        <div className="flex items-center flex-wrap gap-x-2 gap-y-1 text-xs text-[#6B6B6B] mt-2 leading-tight">
          <span className="whitespace-nowrap font-normal">@{creator}</span>
          
          <span className="whitespace-nowrap inline-flex items-center gap-1.5">
            <span className="opacity-40 select-none">·</span>
            <span>{reel.category}</span>
          </span>

          <span className="whitespace-nowrap inline-flex items-center gap-1.5">
            <span className="opacity-40 select-none">·</span>
            <span className="inline-flex items-center gap-1 capitalize">
              <span
                className={`w-1.5 h-1.5 rounded-full ${
                  status === 'reviewed'
                    ? 'bg-[#2F5D50]'
                    : status === 'tried'
                    ? 'bg-[#8A6D3B]'
                    : status === 'archived'
                    ? 'bg-[#6B6B6B]'
                    : 'bg-[#4A6B82]'
                }`}
              />
              {status}
            </span>
          </span>

          {savedDate && (
            <span className="whitespace-nowrap inline-flex items-center gap-1.5">
              <span className="opacity-40 select-none">·</span>
              <span>{savedDate}</span>
            </span>
          )}
        </div>
      </div>

      {/* Right Actions: on mobile always visible 40px touch button, on desktop hover bar */}
      <div className="self-center shrink-0">
        <RowQuickActions
          reel={reel}
          categories={categories}
          onUpdateStatus={onUpdateStatus}
          onUpdateCategory={onUpdateCategory}
          onDelete={onDelete}
        />
      </div>
    </div>
  );
}
