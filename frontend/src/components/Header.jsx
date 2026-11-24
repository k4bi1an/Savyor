import React, { useState, useRef, useEffect } from 'react';
import { Search, Plus, X, LayoutGrid, List, Rows3, ArrowUpDown, Lock, Settings, ArrowLeft } from 'lucide-react';

export default function Header({
  search,
  onSearchChange,
  view = 'grid',
  onViewChange,
  sort = 'newest',
  onSortChange,
  onOpenAdd,
  onOpenSettings,
  onNavigateHome,
  onNavigateBrand,
  onLockVault,
  hasToken,
}) {
  const [isMobileSearchOpen, setIsMobileSearchOpen] = useState(false);
  const searchInputRef = useRef(null);
  const mobileSearchInputRef = useRef(null);

  // Keyboard shortcut '/' focuses search on desktop
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === '/' && document.activeElement !== searchInputRef.current && document.activeElement !== mobileSearchInputRef.current) {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Autofocus mobile search input when mobile search bar opens
  useEffect(() => {
    if (isMobileSearchOpen) {
      setTimeout(() => mobileSearchInputRef.current?.focus(), 50);
    }
  }, [isMobileSearchOpen]);

  // Handle Esc key to close mobile search
  const handleMobileSearchKeyDown = (e) => {
    if (e.key === 'Escape') {
      setIsMobileSearchOpen(false);
    }
  };

  return (
    <header className="sticky top-0 z-30 w-full bg-[#FAFAF8] border-b border-[#E8E6E1]">
      {/* Full-width Mobile Search Bar (active below 640px) */}
      {isMobileSearchOpen ? (
        <div className="max-w-7xl mx-auto px-3 py-2 flex items-center gap-2 sm:hidden">
          <button
            type="button"
            onClick={() => setIsMobileSearchOpen(false)}
            aria-label="Back to navigation"
            className="w-10 h-10 flex items-center justify-center rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
          >
            <ArrowLeft size={18} />
          </button>

          <div className="flex-1 relative">
            <input
              ref={mobileSearchInputRef}
              type="text"
              placeholder="Search reels, notes, or creators..."
              value={search}
              onChange={(e) => onSearchChange(e.target.value)}
              onKeyDown={handleMobileSearchKeyDown}
              className="w-full bg-[#FFFFFF] border border-[#E8E6E1] text-[#1A1A1A] placeholder-[#6B6B6B] text-[13px] h-10 pl-3 pr-8 rounded-[8px] focus:outline-none focus:border-[#2F5D50] focus:ring-1 focus:ring-[#2F5D50]"
            />
            {search && (
              <button
                type="button"
                onClick={() => onSearchChange('')}
                aria-label="Clear search"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 w-6 h-6 flex items-center justify-center text-[#6B6B6B] hover:text-[#1A1A1A]"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>
      ) : (
        /* Standard Header Bar */
        <div className="max-w-7xl mx-auto px-2 sm:px-6 py-2 sm:py-3 flex items-center justify-between gap-1 sm:gap-4">
          {/* Savyor Brand: Mark + lowercase serif wordmark */}
          <button
            onClick={onNavigateHome}
            className="flex items-center gap-1 sm:gap-2 text-left shrink-0 group focus:outline-none"
            title="Savyor home"
            aria-label="Savyor home"
          >
            <img src="/logo-mark.svg" alt="Savyor" className="w-4 h-4 sm:w-[18px] sm:h-[18px] text-[#1A1A1A]" />
            <span className="font-serif text-[17px] sm:text-[22px] font-normal tracking-tight text-[#1A1A1A] lowercase">
              savyor
            </span>
          </button>

          {/* Desktop Search Field (flexible, hidden below 640px) */}
          <div className="flex-1 min-w-[200px] max-w-lg relative hidden sm:block">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[#6B6B6B] pointer-events-none" />
            <input
              ref={searchInputRef}
              type="text"
              placeholder="Search reels, notes, or creators..."
              value={search}
              onChange={(e) => onSearchChange(e.target.value)}
              className="w-full bg-[#FFFFFF] border border-[#E8E6E1] text-[#1A1A1A] placeholder-[#6B6B6B] text-[13px] py-2 pl-9 pr-8 rounded-[8px] focus:outline-none focus:border-[#2F5D50] focus:ring-1 focus:ring-[#2F5D50] transition-colors"
            />
            {search ? (
              <button
                onClick={() => onSearchChange('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#6B6B6B] hover:text-[#1A1A1A]"
                aria-label="Clear search"
              >
                <X size={13} />
              </button>
            ) : (
              <kbd className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-[#6B6B6B] border border-[#E8E6E1] px-1.5 py-0.5 rounded font-mono pointer-events-none hidden lg:block">
                /
              </kbd>
            )}
          </div>

          {/* Toolbar: View Switcher & Sort Dropdown */}
          <div className="flex items-center gap-1 sm:gap-2 shrink-0">
            {/* Mobile Search Button (below 640px) */}
            <button
              type="button"
              onClick={() => setIsMobileSearchOpen(true)}
              title="Search"
              aria-label="Search reels"
              className="sm:hidden w-10 h-10 flex items-center justify-center rounded-[8px] border border-[#E8E6E1] bg-[#FFFFFF] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
            >
              <Search size={14} />
            </button>

            {/* Sort Dropdown */}
            <div className="relative w-[62px] sm:w-auto">
              <select
                value={sort}
                onChange={(e) => onSortChange(e.target.value)}
                aria-label="Sort reels"
                className="w-full min-h-[40px] sm:min-h-0 bg-[#FFFFFF] border border-[#E8E6E1] text-[#1A1A1A] text-[10px] sm:text-xs py-1.5 pl-1.5 sm:pl-2.5 pr-3.5 sm:pr-6 rounded-[8px] focus:outline-none focus:border-[#2F5D50] focus:ring-1 focus:ring-[#2F5D50] cursor-pointer appearance-none transition-colors truncate"
              >
                <option value="newest">Newest</option>
                <option value="oldest">Oldest</option>
                <option value="title">Title</option>
                <option value="reviewed">Reviewed</option>
              </select>
              <ArrowUpDown size={9} className="absolute right-1 top-1/2 -translate-y-1/2 text-[#6B6B6B] pointer-events-none" />
            </div>

            {/* View Toggle (Grid / List / Compact) */}
            <div
              className="flex items-center border border-[#E8E6E1] rounded-[8px] p-0.5 bg-[#FFFFFF]"
              role="group"
              aria-label="View switcher"
            >
              <button
                type="button"
                onClick={() => onViewChange('grid')}
                title="Grid view (g)"
                aria-label="Grid view"
                aria-pressed={view === 'grid'}
                className={`min-h-[40px] min-w-[36px] sm:min-w-0 sm:min-h-0 p-1 sm:p-1.5 rounded-[6px] flex items-center justify-center transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-[#2F5D50] ${
                  view === 'grid'
                    ? 'bg-[#2F5D50] text-[#FAFAF8]'
                    : 'text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#FAFAF8]'
                }`}
              >
                <LayoutGrid size={13} />
              </button>
              <button
                type="button"
                onClick={() => onViewChange('list')}
                title="List view (l)"
                aria-label="List view"
                aria-pressed={view === 'list'}
                className={`min-h-[40px] min-w-[36px] sm:min-w-0 sm:min-h-0 p-1 sm:p-1.5 rounded-[6px] flex items-center justify-center transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-[#2F5D50] ${
                  view === 'list'
                    ? 'bg-[#2F5D50] text-[#FAFAF8]'
                    : 'text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#FAFAF8]'
                }`}
              >
                <List size={13} />
              </button>
              <button
                type="button"
                onClick={() => onViewChange('compact')}
                title="Compact view (c)"
                aria-label="Compact view"
                aria-pressed={view === 'compact'}
                className={`min-h-[40px] min-w-[36px] sm:min-w-0 sm:min-h-0 p-1 sm:p-1.5 rounded-[6px] flex items-center justify-center transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-[#2F5D50] ${
                  view === 'compact'
                    ? 'bg-[#2F5D50] text-[#FAFAF8]'
                    : 'text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#FAFAF8]'
                }`}
              >
                <Rows3 size={13} />
              </button>
            </div>
          </div>

          {/* Right Actions */}
          <div className="flex items-center gap-0.5 sm:gap-2 shrink-0">
            {/* Brand Link: only shown in DEV */}
            {import.meta.env.DEV && (
              <button
                onClick={onNavigateBrand}
                title="View brand assets (/brand)"
                className="hidden sm:inline-block text-[#6B6B6B] hover:text-[#1A1A1A] text-xs py-1.5 px-2 font-normal transition-colors"
              >
                Brand
              </button>
            )}

            {/* Add Reel Button */}
            <button
              onClick={onOpenAdd}
              aria-label="Add reel"
              className="min-h-[40px] sm:min-h-0 p-2 sm:px-3.5 sm:py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-xs font-medium flex items-center gap-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-[#2F5D50]/30"
            >
              <Plus size={14} />
              <span className="hidden sm:inline">Add reel</span>
            </button>

            {/* Settings Dialog Button */}
            {onOpenSettings && (
              <button
                onClick={onOpenSettings}
                title="Settings"
                aria-label="Settings"
                className="min-h-[40px] sm:min-h-0 w-10 sm:w-auto p-1 text-[#6B6B6B] hover:text-[#1A1A1A] flex items-center justify-center rounded-[8px] transition-colors focus:outline-none focus:ring-1 focus:ring-[#2F5D50]"
              >
                <Settings size={14} />
              </button>
            )}

            {/* Lock Button (Icon + Label) */}
            {hasToken && (
              <button
                onClick={onLockVault}
                title="Lock vault"
                aria-label="Lock vault"
                className="min-h-[40px] sm:min-h-0 text-[#6B6B6B] hover:text-[#1A1A1A] text-[11px] sm:text-xs py-1.5 px-1.5 sm:px-2 rounded-[8px] font-normal transition-colors flex items-center gap-1 hover:bg-[#FAFAF8]"
              >
                <Lock size={12} />
                <span>Lock</span>
              </button>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
