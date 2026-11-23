import React, { useState } from 'react';
import { Bookmark, Play } from 'lucide-react';

export default function ReelCard({ reel, onClick, isFocused = false }) {
  const [imgError, setImgError] = useState(false);
  const media = reel.media || {};
  const activeToken = typeof window !== 'undefined' ? (localStorage.getItem('savyor_passcode') || localStorage.getItem('reelvault_token') || '') : '';
  const proxyThumb = media.proxy_thumb_url
    ? `${media.proxy_thumb_url}${activeToken ? `${media.proxy_thumb_url.includes('?') ? '&' : '?'}token=${encodeURIComponent(activeToken)}` : ''}`
    : '';

  // Prefer local proxy thumbnail to avoid adblocker & Instagram hotlink blocks
  const thumbSrc = imgError 
    ? (media.thumbnail || '') 
    : (proxyThumb || media.thumbnail || '');

  const creator = media.channel || media.uploader || 'instagram';
  const status = reel.status || 'unread';

  return (
    <div 
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onClick();
      }}
      className={`group cursor-pointer flex flex-col text-left transition-all p-1 rounded-[14px] ${
        isFocused ? 'ring-1 ring-[#2F5D50] bg-[#F6F5F2]' : 'hover:opacity-95'
      }`}
    >
      {/* 9:16 Thumbnail with NOTHING overlaid except a small play icon on hover (12px radius, 1px border, no shadow) */}
      <div 
        className="w-full relative overflow-hidden rounded-[12px] border border-[#E8E6E1] bg-[#F2F0EB]"
        style={{ aspectRatio: '9/16' }}
      >
        {thumbSrc ? (
          <img
            src={thumbSrc}
            alt={reel.title}
            onError={() => {
              if (!imgError && media.thumbnail) setImgError(true);
            }}
            className="w-full h-full object-cover group-hover:scale-[1.01] transition-transform duration-300"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-[#6B6B6B]">
            <Bookmark size={24} className="opacity-40" />
          </div>
        )}

        {/* Small Play Icon ONLY visible on hover */}
        <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-150 pointer-events-none">
          <div className="w-10 h-10 rounded-full bg-[#1A1A1A]/75 text-[#FAFAF8] flex items-center justify-center">
            <Play size={16} className="fill-[#FAFAF8] translate-x-0.5" />
          </div>
        </div>
      </div>

      {/* 2-line serif title */}
      <h3 className="font-serif text-[16px] text-[#1A1A1A] line-clamp-2 leading-[1.35] mt-2.5 font-normal">
        {reel.title}
      </h3>

      {/* One gray line with creator · category and subtle status dot */}
      <p className="text-xs text-[#6B6B6B] mt-1 truncate flex items-center">
        <span>@{creator}</span>
        <span className="mx-1.5">·</span>
        <span>{reel.category}</span>
        {status !== 'unread' && (
          <>
            <span className="mx-1.5">·</span>
            <span className="capitalize">{status}</span>
          </>
        )}
      </p>
    </div>
  );
}
