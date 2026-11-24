import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { ArrowLeft, Copy, Check, MoreHorizontal, ExternalLink, Trash2, Tag, CheckCircle2, Sparkles, X } from 'lucide-react';
import VideoPlayer from './VideoPlayer';

const ALLOWED_RESOURCE_TYPES = ['github_repo', 'tool', 'product', 'website'];

export default function ReelDetailView({
  reel,
  onBack,
  onDelete,
  onUpdateStatus,
  onUpdateCategory,
  onUpdateTags,
  onAcceptSuggestedCategory,
  onDismissSuggestedCategory,
  categories = [],
  autoMarkReviewed = true,
}) {
  const [activeTab, setActiveTab] = useState('notes');
  const [copied, setCopied] = useState(false);
  const [showMenu, setShowMenu] = useState(false);
  const [showStatusMenu, setShowStatusMenu] = useState(false);
  const [showCategoryMenu, setShowCategoryMenu] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [isEditingTags, setIsEditingTags] = useState(false);
  const [newTagInput, setNewTagInput] = useState('');
  const menuRef = useRef(null);

  const media = reel.media || {};
  const creator = media.channel || media.uploader || 'instagram';
  const status = reel.status || 'unread';

  const videoSrc = media.stream_url || media.direct_video_url || '';
  const posterSrc = media.proxy_thumb_url || media.thumbnail || '';

  // Format creation date
  const savedDate = reel.created_at
    ? new Date(reel.created_at).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    : '';

  // Filter resources to allowed types and non-empty URLs only
  const links = (reel.extracted_resources || []).filter(
    (r) => ALLOWED_RESOURCE_TYPES.includes(r.type) && r.url && r.url.trim().length > 0
  );

  // Auto-mark as reviewed after 3.0 seconds if unread and setting is enabled
  useEffect(() => {
    if (autoMarkReviewed && reel.status === 'unread' && onUpdateStatus) {
      const timer = setTimeout(() => {
        onUpdateStatus(reel.id, 'reviewed');
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [reel.id, reel.status, onUpdateStatus, autoMarkReviewed]);

  // Close menus when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowMenu(false);
        setShowStatusMenu(false);
        setShowCategoryMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleCopyNotes = () => {
    const textToCopy = `# ${reel.title}
@${creator} · ${reel.category}${savedDate ? ` · ${savedDate}` : ''}
Original URL: ${media.webpage_url || ''}

## Summary
${reel.summary}

## Key Takeaways
${(reel.key_takeaways || []).map((t) => `- ${t}`).join('\n')}

## Transcript
${reel.transcript || 'No speech recorded.'}

## Links
${links.map((r) => `- ${r.name} (${r.type}): ${r.url} — ${r.description || ''}`).join('\n')}

## Notes
${reel.structured_content?.items_or_markdown || ''}
`;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(textToCopy).catch(() => {});
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleAddTag = () => {
    const clean = newTagInput.trim().replace(/^#/, '').toLowerCase();
    if (clean && onUpdateTags) {
      const current = reel.tags || [];
      if (!current.includes(clean)) {
        onUpdateTags(reel.id, [...current, clean]);
      }
      setNewTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove) => {
    if (onUpdateTags) {
      const updated = (reel.tags || []).filter((t) => t !== tagToRemove);
      onUpdateTags(reel.id, updated);
    }
  };

  return (
    <div className="w-full max-w-6xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
      {/* Top Back Nav */}
      <button
        onClick={onBack}
        className="inline-flex items-center gap-1.5 text-xs text-[#6B6B6B] hover:text-[#1A1A1A] mb-8 font-normal transition-colors"
      >
        <ArrowLeft size={13} />
        <span>All reels</span>
      </button>

      {/* Main Two-Column Layout */}
      <div className="flex flex-col lg:flex-row gap-8 lg:gap-14 items-start">
        {/* Left: Sticky 340px Video Player */}
        <div className="w-full lg:w-[340px] shrink-0 lg:sticky lg:top-20">
          <VideoPlayer
            src={videoSrc}
            poster={posterSrc}
          />
        </div>

        {/* Right: Readable max-w-[680px] column */}
        <div className="w-full max-w-[680px] flex-1 text-left">
          {/* Top Metadata Row & Actions */}
          <div className="flex items-center justify-between gap-4 border-b border-[#E8E6E1] pb-3">
            <p className="text-xs text-[#6B6B6B] truncate flex items-center flex-wrap gap-1">
              <span>@{creator}</span>
              <span className="mx-1">·</span>
              <span>{reel.category}</span>
              {savedDate && (
                <>
                  <span className="mx-1">·</span>
                  <span>{savedDate}</span>
                </>
              )}
              <span className="mx-1">·</span>
              <span className="capitalize">{status}</span>
            </p>

            {/* Actions: Copy notes icon button & "..." menu */}
            <div className="flex items-center gap-1.5 shrink-0 relative" ref={menuRef}>
              <button
                onClick={handleCopyNotes}
                title="Copy notes"
                className="p-1.5 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#EFECE6] transition-colors"
              >
                {copied ? <Check size={16} className="text-[#2F5D50]" /> : <Copy size={16} />}
              </button>

              <button
                onClick={() => {
                  setShowMenu(!showMenu);
                  setShowStatusMenu(false);
                  setShowCategoryMenu(false);
                }}
                title="More options"
                className="p-1.5 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] hover:bg-[#EFECE6] transition-colors"
              >
                <MoreHorizontal size={16} />
              </button>

              {/* "..." Popover Menu */}
              {showMenu && (
                <div className="absolute right-0 top-8 z-20 w-48 bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs shadow-none">
                  {onUpdateCategory && categories.length > 0 && (
                    <button
                      onClick={() => {
                        setShowCategoryMenu(!showCategoryMenu);
                        setShowStatusMenu(false);
                      }}
                      className="w-full flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] text-left transition-colors"
                    >
                      <span>Change category</span>
                      <Tag size={12} className="text-[#6B6B6B]" />
                    </button>
                  )}

                  {onUpdateStatus && (
                    <button
                      onClick={() => {
                        setShowStatusMenu(!showStatusMenu);
                        setShowCategoryMenu(false);
                      }}
                      className="w-full flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] text-left transition-colors"
                    >
                      <span>Change status</span>
                      <CheckCircle2 size={12} className="text-[#6B6B6B]" />
                    </button>
                  )}

                  {media.webpage_url && (
                    <a
                      href={media.webpage_url}
                      target="_blank"
                      rel="noreferrer"
                      onClick={() => setShowMenu(false)}
                      className="flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] transition-colors"
                    >
                      <span>Open on Instagram</span>
                      <ExternalLink size={12} className="text-[#6B6B6B]" />
                    </a>
                  )}

                  <div className="border-t border-[#E8E6E1] my-1" />

                  <button
                    onClick={() => {
                      setShowMenu(false);
                      setShowDeleteConfirm(true);
                    }}
                    className="w-full flex items-center justify-between px-3 py-2 text-[#B3382C] hover:bg-[#FAFAF8] text-left transition-colors"
                  >
                    <span>Delete reel</span>
                    <Trash2 size={12} />
                  </button>
                </div>
              )}

              {/* Sub-menu: Change Status */}
              {showStatusMenu && onUpdateStatus && (
                <div className="absolute right-52 top-8 z-30 w-36 bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs">
                  {['unread', 'reviewed', 'tried', 'archived'].map((st) => (
                    <button
                      key={st}
                      onClick={() => {
                        onUpdateStatus(reel.id, st);
                        setShowStatusMenu(false);
                        setShowMenu(false);
                      }}
                      className={`w-full text-left px-3 py-1.5 capitalize hover:bg-[#FAFAF8] transition-colors ${
                        status === st ? 'text-[#2F5D50] font-medium' : 'text-[#1A1A1A]'
                      }`}
                    >
                      {st}
                    </button>
                  ))}
                </div>
              )}

              {/* Sub-menu: Change Category */}
              {showCategoryMenu && onUpdateCategory && (
                <div className="absolute right-52 top-8 z-30 w-44 max-h-60 overflow-y-auto bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] py-1 text-xs">
                  {categories.map((cat) => {
                    const catName = typeof cat === 'string' ? cat : cat.name;
                    const catId = typeof cat === 'string' ? cat : cat.id;
                    return (
                      <button
                        key={catId}
                        onClick={() => {
                          onUpdateCategory(reel.id, catId, catName);
                          setShowCategoryMenu(false);
                          setShowMenu(false);
                        }}
                        className={`w-full text-left px-3 py-1.5 hover:bg-[#FAFAF8] transition-colors truncate ${
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
          </div>

          {/* Inline Suggestion Banner for Gemini Suggested Category */}
          {reel.suggested_new_category && (
            <div className="bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] p-3 my-4 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Sparkles size={14} className="text-[#2F5D50]" />
                <span className="text-[#1A1A1A]">
                  Create category <strong>"{reel.suggested_new_category}"</strong> and move this reel?
                </span>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => onAcceptSuggestedCategory?.(reel.id, reel.suggested_new_category)}
                  className="px-2.5 py-1 rounded-[6px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-[11px] font-medium transition-colors"
                >
                  Accept
                </button>
                <button
                  onClick={() => onDismissSuggestedCategory?.(reel.id)}
                  className="px-2 py-1 rounded-[6px] text-[#6B6B6B] hover:text-[#1A1A1A] text-[11px] transition-colors"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}

          {/* Newsreader Serif Title (Up to 3 lines) */}
          <h1 className="font-serif text-2xl sm:text-3xl text-[#1A1A1A] font-normal leading-snug line-clamp-3 my-4">
            {reel.title}
          </h1>

          {/* Tags */}
          <div className="flex items-center flex-wrap gap-1.5 mb-5">
            {(reel.tags || []).map((tag, idx) => (
              <span
                key={idx}
                className="inline-flex items-center gap-1 text-[11px] text-[#6B6B6B] bg-[#F2F0EB] px-2 py-0.5 rounded-[6px]"
              >
                #{tag}
                {isEditingTags && (
                  <button onClick={() => handleRemoveTag(tag)} className="hover:text-[#B3382C]">
                    <X size={10} />
                  </button>
                )}
              </span>
            ))}
            {isEditingTags ? (
              <div className="inline-flex items-center gap-1.5">
                <input
                  type="text"
                  placeholder="new tag..."
                  value={newTagInput}
                  onChange={(e) => setNewTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddTag();
                    }
                  }}
                  className="bg-[#FAFAF8] border border-[#E8E6E1] text-[11px] px-2 py-0.5 rounded-[6px] w-20 focus:outline-none focus:border-[#2F5D50]"
                />
                <button
                  onClick={() => setIsEditingTags(false)}
                  className="text-[11px] text-[#2F5D50] hover:underline"
                >
                  Done
                </button>
              </div>
            ) : (
              <button
                onClick={() => setIsEditingTags(true)}
                className="text-[11px] text-[#6B6B6B] hover:text-[#1A1A1A] px-1"
              >
                + Edit tags
              </button>
            )}
          </div>

          {/* Underline Tabs: Notes, Transcript, Links */}
          <div className="flex items-center gap-6 border-b border-[#E8E6E1] mb-6 text-xs">
            <button
              onClick={() => setActiveTab('notes')}
              className={`pb-2.5 transition-colors border-b-2 font-normal ${
                activeTab === 'notes'
                  ? 'border-[#2F5D50] text-[#2F5D50] font-medium'
                  : 'border-transparent text-[#6B6B6B] hover:text-[#1A1A1A]'
              }`}
            >
              Notes
            </button>

            <button
              onClick={() => setActiveTab('transcript')}
              className={`pb-2.5 transition-colors border-b-2 font-normal ${
                activeTab === 'transcript'
                  ? 'border-[#2F5D50] text-[#2F5D50] font-medium'
                  : 'border-transparent text-[#6B6B6B] hover:text-[#1A1A1A]'
              }`}
            >
              Transcript
            </button>

            <button
              onClick={() => setActiveTab('links')}
              className={`pb-2.5 transition-colors border-b-2 font-normal ${
                activeTab === 'links'
                  ? 'border-[#2F5D50] text-[#2F5D50] font-medium'
                  : 'border-transparent text-[#6B6B6B] hover:text-[#1A1A1A]'
              }`}
            >
              Links ({links.length})
            </button>
          </div>

          {/* Tab 1: Notes (Rendered as normal prose, no boxed items) */}
          {activeTab === 'notes' && (
            <div className="space-y-6 text-[#1A1A1A]">
              {/* Summary paragraph */}
              {reel.summary && (
                <div>
                  <p className="text-[15px] leading-relaxed text-[#1A1A1A] font-normal">
                    {reel.summary}
                  </p>
                </div>
              )}

              {/* Key Takeaways as simple bullets */}
              {(reel.key_takeaways || []).length > 0 && (
                <div className="pt-2">
                  <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-3">
                    Key takeaways
                  </h2>
                  <ul className="list-disc list-outside ml-5 space-y-2 text-[15px] leading-relaxed text-[#1A1A1A]">
                    {reel.key_takeaways.map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Structured Deep Dive as standard markdown prose */}
              {reel.structured_content && (
                <div className="pt-2">
                  <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-3">
                    {reel.structured_content.heading || 'Notes'}
                  </h2>
                  <div className="prose prose-neutral max-w-none text-[15px] leading-relaxed text-[#1A1A1A]">
                    <ReactMarkdown
                      components={{
                        code({ node, inline, className, children, ...props }) {
                          if (inline) {
                            return (
                              <code className="bg-[#F2F1EC] text-[#1A1A1A] px-1.5 py-0.5 rounded text-[13px] font-mono" {...props}>
                                {children}
                              </code>
                            );
                          }
                          return (
                            <pre className="bg-[#F2F1EC] text-[#1A1A1A] p-3 rounded-[8px] overflow-x-auto text-[13px] font-mono my-3 border border-[#E8E6E1]/50">
                              <code {...props}>{children}</code>
                            </pre>
                          );
                        },
                      }}
                    >
                      {reel.structured_content.items_or_markdown}
                    </ReactMarkdown>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Tab 2: Transcript */}
          {activeTab === 'transcript' && (
            <div className="text-[#1A1A1A]">
              <p className="text-[15px] leading-relaxed whitespace-pre-wrap font-normal">
                {reel.transcript || 'No speech in video (music/silent).'}
              </p>
            </div>
          )}

          {/* Tab 3: Links (Only github_repo, tool, product, website with non-empty URL) */}
          {activeTab === 'links' && (
            <div className="space-y-4">
              {links.length === 0 ? (
                <p className="text-xs text-[#6B6B6B]">No links or tools detected in this reel.</p>
              ) : (
                <div className="divide-y divide-[#E8E6E1]">
                  {links.map((res, i) => (
                    <div key={i} className="py-3 first:pt-0 last:pb-0">
                      <div className="flex items-baseline gap-2">
                        <a
                          href={res.url}
                          target="_blank"
                          rel="noreferrer"
                          className="text-[15px] font-medium text-[#2F5D50] hover:underline flex items-center gap-1"
                        >
                          <span>{res.name}</span>
                          <ExternalLink size={11} className="text-[#6B6B6B]" />
                        </a>
                        <span className="text-xs text-[#6B6B6B] capitalize">
                          · {res.type.replace('_', ' ')}
                        </span>
                      </div>
                      {res.description && (
                        <p className="text-xs text-[#6B6B6B] mt-1 leading-relaxed">
                          {res.description}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="w-full max-w-sm bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left shadow-none">
            <h3 className="font-serif text-lg font-medium text-[#1A1A1A] mb-2">
              Delete reel?
            </h3>
            <p className="text-xs text-[#6B6B6B] leading-relaxed mb-6">
              This will permanently remove the video and all associated notes from your vault.
            </p>
            <div className="flex items-center justify-end gap-2.5">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="px-3.5 py-2 rounded-[8px] border border-[#E8E6E1] text-[#1A1A1A] hover:bg-[#FAFAF8] text-xs font-normal transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  setShowDeleteConfirm(false);
                  onDelete(reel.id);
                }}
                className="px-3.5 py-2 rounded-[8px] bg-[#B3382C] hover:bg-[#992E24] text-white text-xs font-medium transition-colors"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
