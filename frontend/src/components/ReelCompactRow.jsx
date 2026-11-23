import React from 'react';
import RowQuickActions from './RowQuickActions';

export default function ReelCompactRow({
  reel,
  onClick,
  isFocused = false,
  categories = [],
  onUpdateStatus,
  onUpdateCategory,
  onDelete,
}) {
  const status = reel.status || 'unread';

  const savedDate = reel.created_at
    ? new Date(reel.created_at).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
      })
    : '';

  return (
    <div
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onClick();
      }}
      className={`group relative h-11 px-3 flex items-center justify-between gap-3 border-b border-[#E8E6E1] text-left cursor-pointer transition-colors hover:bg-[#F6F5F2] ${
        isFocused ? 'ring-1 ring-[#2F5D50] bg-[#F6F5F2] rounded-[6px]' : ''
      }`}
    >
      {/* Left: Title + Category */}
      <div className="flex items-center gap-3 min-w-0 flex-1">
        {/* Status dot */}
        <span
          title={`Status: ${status}`}
          className={`w-2 h-2 rounded-full shrink-0 ${
            status === 'reviewed'
              ? 'bg-[#2F5D50]'
              : status === 'tried'
              ? 'bg-[#8A6D3B]'
              : status === 'archived'
              ? 'bg-[#6B6B6B]'
              : 'bg-[#4A6B82]'
          }`}
        />

        {/* Title */}
        <span className="font-serif text-[14px] text-[#1A1A1A] font-normal truncate flex-1">
          {reel.title}
        </span>

        {/* Category */}
        <span className="hidden sm:inline-block text-xs text-[#6B6B6B] shrink-0 max-w-[140px] truncate">
          {reel.category}
        </span>

        {/* Date */}
        {savedDate && (
          <span className="hidden md:inline-block text-xs text-[#6B6B6B]/80 shrink-0">
            {savedDate}
          </span>
        )}
      </div>

      {/* Right: Quick Actions */}
      <div className="shrink-0 flex items-center">
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
