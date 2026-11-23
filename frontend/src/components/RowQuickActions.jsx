import React, { useState, useRef, useEffect } from 'react';
import { MoreHorizontal, ExternalLink, Trash2, CheckCircle2, Tag } from 'lucide-react';

export default function RowQuickActions({ reel, categories = [], onUpdateStatus, onUpdateCategory, onDelete }) {
  const [showMenu, setShowMenu] = useState(false);
  const [showStatusSubmenu, setShowStatusSubmenu] = useState(false);
  const [showCategorySubmenu, setShowCategorySubmenu] = useState(false);
  const menuRef = useRef(null);

  const media = reel.media || {};
  const status = reel.status || 'unread';

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowMenu(false);
        setShowStatusSubmenu(false);
        setShowCategorySubmenu(false);
      }
    };
    if (showMenu) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showMenu]);

  return (
    <div 
      ref={menuRef} 
      className="relative flex items-center gap-1 shrink-0" 
      onClick={(e) => e.stopPropagation()}
    >
      {/* Mobile Touch 40px Menu Button (Always visible on mobile/touch screens) */}
      <div className="sm:hidden flex items-center justify-center">
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            setShowMenu(!showMenu);
            setShowStatusSubmenu(false);
            setShowCategorySubmenu(false);
          }}
          title="Quick actions"
          aria-label="Quick actions"
          aria-expanded={showMenu}
          className="w-10 h-10 flex items-center justify-center rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] active:bg-[#E8E6E1]/60 transition-colors focus:outline-none"
        >
          <MoreHorizontal size={18} />
        </button>
      </div>

      {/* Desktop Hover Action Bar (Hidden below 640px, revealed on row hover/focus) */}
      <div className="hidden sm:flex items-center gap-1 sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100 transition-opacity duration-150">
        {/* Quick Instagram link button */}
        {media.webpage_url && (
          <a
            href={media.webpage_url}
            target="_blank"
            rel="noreferrer"
            title="Open on Instagram"
            aria-label="Open on Instagram"
            className="p-1.5 rounded-[6px] text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#E8E6E1]/60 transition-colors"
          >
            <ExternalLink size={13} />
          </a>
        )}

        {/* More Options Menu Button */}
        <button
          type="button"
          onClick={() => {
            setShowMenu(!showMenu);
            setShowStatusSubmenu(false);
            setShowCategorySubmenu(false);
          }}
          title="Quick actions"
          aria-label="Quick actions"
          aria-expanded={showMenu}
          className="p-1.5 rounded-[6px] text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#E8E6E1]/60 transition-colors focus:outline-none focus:ring-1 focus:ring-[#2F5D50]"
        >
          <MoreHorizontal size={14} />
        </button>
      </div>

      {/* Popover Menu */}
      {showMenu && (
        <div className="absolute right-0 top-10 sm:top-7 z-30 w-44 bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs shadow-none">
          {onUpdateStatus && (
            <button
              type="button"
              onClick={() => {
                setShowStatusSubmenu(!showStatusSubmenu);
                setShowCategorySubmenu(false);
              }}
              className="w-full min-h-[36px] flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] text-left transition-colors"
            >
              <span>Change status</span>
              <CheckCircle2 size={13} className="text-[#6B6B6B]" />
            </button>
          )}

          {onUpdateCategory && categories.length > 0 && (
            <button
              type="button"
              onClick={() => {
                setShowCategorySubmenu(!showCategorySubmenu);
                setShowStatusSubmenu(false);
              }}
              className="w-full min-h-[36px] flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] text-left transition-colors"
            >
              <span>Change category</span>
              <Tag size={13} className="text-[#6B6B6B]" />
            </button>
          )}

          {media.webpage_url && (
            <a
              href={media.webpage_url}
              target="_blank"
              rel="noreferrer"
              onClick={() => setShowMenu(false)}
              className="min-h-[36px] flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
            >
              <span>Open on Instagram</span>
              <ExternalLink size={13} className="text-[#6B6B6B]" />
            </a>
          )}

          <div className="border-t border-[#E8E6E1] my-1" />

          <button
            type="button"
            onClick={() => {
              setShowMenu(false);
              onDelete(reel.id);
            }}
            className="w-full min-h-[36px] flex items-center justify-between px-3 py-2 text-[#B3382C] hover:bg-[#FAFAF8] text-left transition-colors"
          >
            <span>Delete</span>
            <Trash2 size={13} />
          </button>
        </div>
      )}

      {/* Status Submenu */}
      {showStatusSubmenu && onUpdateStatus && (
        <div className="absolute right-48 top-10 sm:top-7 z-40 w-32 bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs">
          {['unread', 'reviewed', 'tried', 'archived'].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => {
                onUpdateStatus(reel.id, st);
                setShowStatusSubmenu(false);
                setShowMenu(false);
              }}
              className={`w-full min-h-[36px] text-left px-3 py-2 capitalize hover:bg-[#FAFAF8] transition-colors ${
                status === st ? 'text-[#2F5D50] font-medium' : 'text-[#1A1A1A]'
              }`}
            >
              {st}
            </button>
          ))}
        </div>
      )}

      {/* Category Submenu */}
      {showCategorySubmenu && onUpdateCategory && (
        <div className="absolute right-48 top-10 sm:top-7 z-40 w-44 max-h-56 overflow-y-auto bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs">
          {categories.map((cat) => {
            const catName = typeof cat === 'string' ? cat : cat.name;
            const catId = typeof cat === 'string' ? cat : cat.id;
            return (
              <button
                key={catId}
                type="button"
                onClick={() => {
                  onUpdateCategory(reel.id, catId, catName);
                  setShowCategorySubmenu(false);
                  setShowMenu(false);
                }}
                className={`w-full min-h-[36px] text-left px-3 py-2 hover:bg-[#FAFAF8] transition-colors truncate ${
                  reel.category === catName ? 'text-[#2F5D50] font-medium' : 'text-[#1A1A1A]'
                }`}
              >
                {catName}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
