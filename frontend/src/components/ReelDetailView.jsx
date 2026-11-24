import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import {
  ArrowLeft, Copy, Check, MoreHorizontal, ExternalLink, Trash2, Tag,
  CheckCircle2, Sparkles, X, RefreshCw, PlayCircle, Search, Clock, AlertCircle
} from 'lucide-react';
import VideoPlayer from './VideoPlayer';

const ALLOWED_RESOURCE_TYPES = ['github_repo', 'tool', 'product', 'website', 'book/media', 'place', 'book'];

export default function ReelDetailView({
  reel,
  onBack,
  onDelete,
  onUpdateStatus,
  onUpdateCategory,
  onUpdateTags,
  onAcceptSuggestedCategory,
  onDismissSuggestedCategory,
  onReprocess,
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
  const [recheckingLinks, setRecheckingLinks] = useState(false);
  const [currentReel, setCurrentReel] = useState(reel);

  // Video seeking state
  const [videoTime, setVideoTime] = useState(0);
  const [seekTo, setSeekTo] = useState(null);
  const playerContainerRef = useRef(null);
  const menuRef = useRef(null);

  useEffect(() => {
    setCurrentReel(reel);
  }, [reel]);

  const media = currentReel.media || {};
  const creator = media.channel || media.uploader || 'instagram';
  const status = currentReel.status || 'unread';
  const noteType = currentReel.note_type || 'general';
  const typeData = currentReel.type_data || {};
  const keyMoments = currentReel.key_moments || [];
  const timestampedTranscript = currentReel.timestamped_transcript || [];

  const videoSrc = media.stream_url || media.direct_video_url || '';
  const posterSrc = media.proxy_thumb_url || media.thumbnail || '';

  const savedDate = currentReel.created_at
    ? new Date(currentReel.created_at).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    : '';

  // Filter resources to allowed types
  const resources = currentReel.extracted_resources || [];

  // Auto-mark as reviewed after 3.0 seconds if unread and setting is enabled
  useEffect(() => {
    if (autoMarkReviewed && currentReel.status === 'unread' && onUpdateStatus) {
      const timer = setTimeout(() => {
        onUpdateStatus(currentReel.id, 'reviewed');
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [currentReel.id, currentReel.status, onUpdateStatus, autoMarkReviewed]);

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

  const handleSeek = (seconds) => {
    setSeekTo({ time: seconds, timestamp: Date.now() });
    if (playerContainerRef.current && window.innerWidth < 1024) {
      playerContainerRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const formatSeconds = (secs) => {
    if (!secs || isNaN(secs)) return '0:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const handleCopyNotes = () => {
    let typeSpecificMarkdown = '';
    if (noteType === 'recipe' && typeData) {
      typeSpecificMarkdown = `\n## Recipe Details\n${typeData.servings ? `**Servings:** ${typeData.servings}\n` : ''}${typeData.prep_or_cook_time ? `**Time:** ${typeData.prep_or_cook_time}\n` : ''}\n### Ingredients\n${(typeData.ingredients || []).map((i) => `- ${i.name}: ${i.quantity}`).join('\n')}\n\n### Steps\n${(typeData.steps || []).map((s, idx) => `${idx + 1}. ${s}`).join('\n')}\n`;
    } else if (noteType === 'workout' && typeData) {
      typeSpecificMarkdown = `\n## Workout Plan\n${typeData.goal ? `**Goal:** ${typeData.goal}\n` : ''}${typeData.equipment ? `**Equipment:** ${(typeData.equipment || []).join(', ')}\n` : ''}\n### Exercises\n${(typeData.exercises || []).map((ex) => `- **${ex.name}**: ${ex.sets || ''} sets ${ex.reps_or_duration || ''} ${ex.notes ? `(${ex.notes})` : ''}`).join('\n')}\n`;
    } else if (noteType === 'tutorial' && typeData) {
      typeSpecificMarkdown = `\n## Tutorial\n${(typeData.steps || []).map((st) => `### Step ${st.step_number}: ${st.description}\n${st.code_snippet ? `\`\`\`${st.language || ''}\n${st.code_snippet}\n\`\`\`\n` : ''}`).join('\n')}\n`;
    } else if (noteType === 'tool_or_product' && typeData) {
      typeSpecificMarkdown = `\n## Product Details\n**What it does:** ${typeData.what_it_does || ''}\n${typeData.price_if_stated ? `**Price:** ${typeData.price_if_stated}\n` : ''}\n### Key Features\n${(typeData.key_features || []).map((f) => `- ${f}`).join('\n')}\n`;
    }

    const textToCopy = `# ${currentReel.title}
@${creator} · ${currentReel.category} · ${noteType.replace('_', ' ')}${savedDate ? ` · ${savedDate}` : ''}
Original URL: ${media.webpage_url || ''}

## Summary
${currentReel.summary}

## Key Takeaways
${(currentReel.key_takeaways || []).map((t) => `- ${t}`).join('\n')}
${typeSpecificMarkdown}
## Key Moments
${keyMoments.map((km) => `- [${formatSeconds(km.time)}] ${km.label}`).join('\n')}

## Links
${resources.map((r) => `- ${r.name} (${r.type}): ${r.url || 'No URL'} — ${r.description || ''}`).join('\n')}

## Transcript
${currentReel.transcript || 'No speech recorded.'}
`;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(textToCopy).catch(() => {});
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleRecheckLinks = async () => {
    setRecheckingLinks(true);
    try {
      const res = await fetch(`/api/reels/${currentReel.id}/verify-links`, {
        method: 'POST',
        credentials: 'include',
      });
      if (res.ok) {
        const data = await res.json();
        setCurrentReel((prev) => ({
          ...prev,
          extracted_resources: data.extracted_resources,
        }));
      }
    } catch (e) {
      console.error('Failed to recheck links:', e);
    } finally {
      setRecheckingLinks(false);
    }
  };

  const handleAddTag = () => {
    const clean = newTagInput.trim().replace(/^#/, '').toLowerCase();
    if (clean && onUpdateTags) {
      const current = currentReel.tags || [];
      if (!current.includes(clean)) {
        onUpdateTags(currentReel.id, [...current, clean]);
      }
      setNewTagInput('');
    }
  };

  const handleRemoveTag = (tagToRemove) => {
    if (onUpdateTags) {
      const updated = (currentReel.tags || []).filter((t) => t !== tagToRemove);
      onUpdateTags(currentReel.id, updated);
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
        <div ref={playerContainerRef} className="w-full lg:w-[340px] shrink-0 lg:sticky lg:top-20">
          <VideoPlayer
            src={videoSrc}
            poster={posterSrc}
            onTimeUpdate={(t) => setVideoTime(t)}
            seekTo={seekTo}
          />
        </div>

        {/* Right: Readable max-w-[680px] column */}
        <div className="w-full max-w-[680px] flex-1 text-left">
          {/* Top Metadata Row & Actions */}
          <div className="flex items-center justify-between gap-4 border-b border-[#E8E6E1] pb-3">
            <p className="text-xs text-[#6B6B6B] truncate flex items-center flex-wrap gap-1">
              <span>@{creator}</span>
              <span className="mx-1">·</span>
              <span>{currentReel.category}</span>
              <span className="mx-1">·</span>
              <span className="bg-[#EFECE6] text-[#2F5D50] px-1.5 py-0.5 rounded-[4px] capitalize font-medium text-[11px]">
                {noteType.replace('_', ' ')}
              </span>
              {savedDate && (
                <>
                  <span className="mx-1">·</span>
                  <span>{savedDate}</span>
                </>
              )}
              <span className="mx-1">·</span>
              <span className="capitalize">{status}</span>
            </p>

            {/* Actions: Copy notes icon button, Reprocess, & "..." menu */}
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
                  {onReprocess && (
                    <button
                      onClick={() => {
                        setShowMenu(false);
                        onReprocess(currentReel.id);
                      }}
                      className="w-full flex items-center justify-between px-3 py-2 text-[#1A1A1A] hover:bg-[#FAFAF8] text-left transition-colors"
                    >
                      <span>Reprocess with AI</span>
                      <RefreshCw size={12} className="text-[#6B6B6B]" />
                    </button>
                  )}

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
                        onUpdateStatus(currentReel.id, st);
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
                          onUpdateCategory(currentReel.id, catId, catName);
                          setShowCategoryMenu(false);
                          setShowMenu(false);
                        }}
                        className={`w-full text-left px-3 py-1.5 hover:bg-[#FAFAF8] transition-colors truncate ${
                          currentReel.category === catName ? 'text-[#2F5D50] font-medium' : 'text-[#1A1A1A]'
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
          {currentReel.suggested_new_category && (
            <div className="bg-[#FFFFFF] border border-[#E8E6E1] rounded-[8px] p-3 my-4 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Sparkles size={14} className="text-[#2F5D50]" />
                <span className="text-[#1A1A1A]">
                  Create category <strong>"{currentReel.suggested_new_category}"</strong> and move this reel?
                </span>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => onAcceptSuggestedCategory?.(currentReel.id, currentReel.suggested_new_category)}
                  className="px-2.5 py-1 rounded-[6px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-[11px] font-medium transition-colors"
                >
                  Accept
                </button>
                <button
                  onClick={() => onDismissSuggestedCategory?.(currentReel.id)}
                  className="px-2 py-1 rounded-[6px] text-[#6B6B6B] hover:text-[#1A1A1A] text-[11px] transition-colors"
                >
                  Dismiss
                </button>
              </div>
            </div>
          )}

          {/* Newsreader Serif Title */}
          <h1 className="font-serif text-2xl sm:text-3xl text-[#1A1A1A] font-normal leading-snug line-clamp-3 my-4">
            {currentReel.title}
          </h1>

          {/* Tags */}
          <div className="flex items-center flex-wrap gap-1.5 mb-5">
            {(currentReel.tags || []).map((tag, idx) => (
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
              Links ({resources.length})
            </button>
          </div>

          {/* Tab 1: Notes (Template-Specific Content) */}
          {activeTab === 'notes' && (
            <div className="space-y-6 text-[#1A1A1A]">
              {/* Summary paragraph */}
              {currentReel.summary && (
                <div>
                  <p className="text-[15px] leading-relaxed text-[#1A1A1A] font-normal">
                    {currentReel.summary}
                  </p>
                </div>
              )}

              {/* Key Moments seeking list */}
              {keyMoments.length > 0 && (
                <div className="border border-[#E8E6E1] rounded-[8px] p-3.5 bg-[#FFFFFF]">
                  <h3 className="font-serif text-sm font-medium text-[#1A1A1A] mb-2.5 flex items-center gap-1.5">
                    <Clock size={14} className="text-[#2F5D50]" />
                    Key moments
                  </h3>
                  <div className="space-y-1.5">
                    {keyMoments.map((km, idx) => (
                      <div key={idx} className="flex items-center gap-2 text-xs">
                        <button
                          onClick={() => handleSeek(km.time)}
                          className="font-mono text-[#2F5D50] hover:underline bg-[#F2F1EC] px-1.5 py-0.5 rounded text-[11px] shrink-0"
                          title="Jump to video moment"
                        >
                          {formatSeconds(km.time)}
                        </button>
                        <span className="text-[#1A1A1A]">{km.label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Key Takeaways as simple bullets */}
              {(currentReel.key_takeaways || []).length > 0 && (
                <div className="pt-2">
                  <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-3">
                    Key takeaways
                  </h2>
                  <ul className="list-disc list-outside ml-5 space-y-2 text-[15px] leading-relaxed text-[#1A1A1A]">
                    {currentReel.key_takeaways.map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* TEMPLATE 1: RECIPE */}
              {noteType === 'recipe' && typeData && (
                <div className="space-y-5 pt-2">
                  <div className="flex items-center gap-4 text-xs text-[#6B6B6B] border-b border-[#E8E6E1] pb-2">
                    {typeData.servings && <span><strong>Servings:</strong> {typeData.servings}</span>}
                    {typeData.prep_or_cook_time && <span><strong>Time:</strong> {typeData.prep_or_cook_time}</span>}
                  </div>

                  {typeData.ingredients && typeData.ingredients.length > 0 && (
                    <div>
                      <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-2.5">
                        Ingredients
                      </h2>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                        {typeData.ingredients.map((ing, i) => (
                          <div key={i} className="flex items-center justify-between p-2 rounded bg-[#F8F7F4] border border-[#E8E6E1]/50">
                            <span className="font-medium text-[#1A1A1A]">{ing.name}</span>
                            <span className="text-[#6B6B6B]">{ing.quantity}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {typeData.steps && typeData.steps.length > 0 && (
                    <div>
                      <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-2.5">
                        Instructions
                      </h2>
                      <ol className="list-decimal list-outside ml-5 space-y-2 text-[15px] leading-relaxed">
                        {typeData.steps.map((st, i) => (
                          <li key={i}>{st}</li>
                        ))}
                      </ol>
                    </div>
                  )}
                </div>
              )}

              {/* TEMPLATE 2: WORKOUT */}
              {noteType === 'workout' && typeData && (
                <div className="space-y-5 pt-2">
                  <div className="flex items-center flex-wrap gap-4 text-xs text-[#6B6B6B] border-b border-[#E8E6E1] pb-2">
                    {typeData.goal && <span><strong>Goal:</strong> {typeData.goal}</span>}
                    {typeData.equipment && typeData.equipment.length > 0 && (
                      <span><strong>Equipment:</strong> {typeData.equipment.join(', ')}</span>
                    )}
                  </div>

                  {typeData.exercises && typeData.exercises.length > 0 && (
                    <div>
                      <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-2.5">
                        Exercise Routine
                      </h2>
                      <div className="border border-[#E8E6E1] rounded-[8px] overflow-hidden">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-[#F8F7F4] border-b border-[#E8E6E1] text-[#6B6B6B]">
                            <tr>
                              <th className="py-2 px-3 font-normal">Exercise</th>
                              <th className="py-2 px-3 font-normal">Sets</th>
                              <th className="py-2 px-3 font-normal">Reps / Duration</th>
                              <th className="py-2 px-3 font-normal">Notes</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-[#E8E6E1]">
                            {typeData.exercises.map((ex, i) => (
                              <tr key={i} className="hover:bg-[#FAFAF8]">
                                <td className="py-2 px-3 font-medium text-[#1A1A1A]">{ex.name}</td>
                                <td className="py-2 px-3 text-[#6B6B6B]">{ex.sets || '—'}</td>
                                <td className="py-2 px-3 text-[#6B6B6B]">{ex.reps_or_duration || '—'}</td>
                                <td className="py-2 px-3 text-[#6B6B6B]">{ex.notes || '—'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {typeData.cautions && typeData.cautions.length > 0 && (
                    <div className="p-3 bg-[#FAF7F6] border border-[#E8E6E1] rounded-[8px] text-xs">
                      <strong className="text-[#B3382C] block mb-1">Cautions & Form Tips:</strong>
                      <ul className="list-disc ml-4 space-y-1 text-[#6B6B6B]">
                        {typeData.cautions.map((c, i) => (
                          <li key={i}>{c}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* TEMPLATE 3: TUTORIAL */}
              {noteType === 'tutorial' && typeData && (
                <div className="space-y-5 pt-2">
                  {typeData.prerequisites && typeData.prerequisites.length > 0 && (
                    <div className="p-3 bg-[#F8F7F4] border border-[#E8E6E1] rounded-[8px] text-xs">
                      <strong className="text-[#1A1A1A] block mb-1">Prerequisites:</strong>
                      <ul className="list-disc ml-4 space-y-1 text-[#6B6B6B]">
                        {typeData.prerequisites.map((p, i) => (
                          <li key={i}>{p}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {typeData.steps && typeData.steps.length > 0 && (
                    <div className="space-y-4">
                      <h2 className="font-serif text-lg font-medium text-[#1A1A1A]">
                        Tutorial Steps
                      </h2>
                      {typeData.steps.map((st, i) => (
                        <div key={i} className="p-3.5 border border-[#E8E6E1] rounded-[8px] bg-[#FFFFFF] space-y-2">
                          <h4 className="font-medium text-sm text-[#1A1A1A]">
                            Step {st.step_number || i + 1}: {st.description}
                          </h4>
                          {st.code_snippet && (
                            <pre className="bg-[#F2F1EC] text-[#1A1A1A] p-2.5 rounded-[6px] overflow-x-auto text-[12px] font-mono border border-[#E8E6E1]/50">
                              <code>{st.code_snippet}</code>
                            </pre>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {typeData.result && (
                    <div className="text-xs text-[#2F5D50] p-2.5 rounded bg-[#E4ECE8]/50 border border-[#2F5D50]/20">
                      <strong>Expected Result:</strong> {typeData.result}
                    </div>
                  )}
                </div>
              )}

              {/* TEMPLATE 4: TOOL OR PRODUCT */}
              {noteType === 'tool_or_product' && typeData && (
                <div className="space-y-4 pt-2">
                  <div className="p-4 border border-[#E8E6E1] rounded-[8px] bg-[#FFFFFF] space-y-3">
                    <h3 className="font-serif text-lg font-medium text-[#1A1A1A]">
                      {typeData.name || currentReel.title}
                    </h3>
                    <p className="text-xs text-[#6B6B6B] leading-relaxed">
                      {typeData.what_it_does}
                    </p>
                    {typeData.price_if_stated && (
                      <p className="text-xs text-[#1A1A1A]">
                        <strong>Pricing:</strong> {typeData.price_if_stated}
                      </p>
                    )}
                    {typeData.key_features && typeData.key_features.length > 0 && (
                      <div>
                        <strong className="text-xs text-[#1A1A1A] block mb-1.5">Key Features:</strong>
                        <ul className="list-disc ml-4 space-y-1 text-xs text-[#6B6B6B]">
                          {typeData.key_features.map((f, i) => (
                            <li key={i}>{f}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {typeData.alternatives_mentioned && typeData.alternatives_mentioned.length > 0 && (
                      <p className="text-xs text-[#6B6B6B]">
                        <strong>Alternatives:</strong> {typeData.alternatives_mentioned.join(', ')}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* TEMPLATE 5: PLACE */}
              {noteType === 'place' && typeData && (
                <div className="space-y-4 pt-2">
                  <div className="p-4 border border-[#E8E6E1] rounded-[8px] bg-[#FFFFFF] space-y-2.5 text-xs">
                    <h3 className="font-serif text-lg font-medium text-[#1A1A1A]">
                      {typeData.name || currentReel.title}
                    </h3>
                    <p className="text-[#6B6B6B]">
                      <strong>Location:</strong> {typeData.location_as_stated || 'Not stated'}
                    </p>
                    {typeData.best_time && (
                      <p className="text-[#6B6B6B]">
                        <strong>Best Time:</strong> {typeData.best_time}
                      </p>
                    )}
                    {typeData.what_to_do_or_eat && typeData.what_to_do_or_eat.length > 0 && (
                      <div>
                        <strong className="block text-[#1A1A1A] mt-2 mb-1">Highlights:</strong>
                        <ul className="list-disc ml-4 space-y-1 text-[#6B6B6B]">
                          {typeData.what_to_do_or_eat.map((item, i) => (
                            <li key={i}>{item}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {typeData.tips && typeData.tips.length > 0 && (
                      <div>
                        <strong className="block text-[#1A1A1A] mt-2 mb-1">Tips:</strong>
                        <ul className="list-disc ml-4 space-y-1 text-[#6B6B6B]">
                          {typeData.tips.map((tip, i) => (
                            <li key={i}>{tip}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* TEMPLATE 6: BOOK OR MEDIA */}
              {noteType === 'book_or_media' && typeData && (
                <div className="space-y-4 pt-2 text-xs">
                  <div className="p-4 border border-[#E8E6E1] rounded-[8px] bg-[#FFFFFF] space-y-2">
                    <h3 className="font-serif text-lg font-medium text-[#1A1A1A]">
                      {typeData.title || currentReel.title}
                    </h3>
                    {typeData.creator && (
                      <p className="text-[#6B6B6B]">
                        <strong>Author / Creator:</strong> {typeData.creator}
                      </p>
                    )}
                    {typeData.why_recommended && (
                      <p className="text-[#1A1A1A] leading-relaxed">
                        <strong>Why Recommended:</strong> {typeData.why_recommended}
                      </p>
                    )}
                    {typeData.where_to_find && (
                      <p className="text-[#6B6B6B]">
                        <strong>Where to find:</strong> {typeData.where_to_find}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* TEMPLATE 7: ADVICE */}
              {noteType === 'advice' && typeData && (
                <div className="space-y-4 pt-2 text-xs">
                  <div className="p-4 border-l-2 border-[#2F5D50] bg-[#F8F7F4] rounded-r-[8px] space-y-2">
                    <h3 className="font-serif text-base font-medium text-[#1A1A1A]">
                      "{typeData.main_claim || currentReel.summary}"
                    </h3>
                  </div>
                  {typeData.supporting_points && typeData.supporting_points.length > 0 && (
                    <div>
                      <strong className="block text-[#1A1A1A] mb-1.5">Key Arguments:</strong>
                      <ul className="list-disc ml-4 space-y-1 text-xs text-[#6B6B6B]">
                        {typeData.supporting_points.map((pt, i) => (
                          <li key={i}>{pt}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {typeData.caveats && typeData.caveats.length > 0 && (
                    <div className="p-3 bg-[#FAF7F6] border border-[#E8E6E1] rounded-[8px]">
                      <strong className="text-[#B3382C] block mb-1">Caveats & Nuance:</strong>
                      <ul className="list-disc ml-4 space-y-1 text-xs text-[#6B6B6B]">
                        {typeData.caveats.map((c, i) => (
                          <li key={i}>{c}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              {/* Structured Deep Dive Markdown from Gemini */}
              {currentReel.structured_content && currentReel.structured_content.items_or_markdown && (
                <div className="pt-2">
                  <h2 className="font-serif text-lg font-medium text-[#1A1A1A] mb-3">
                    {currentReel.structured_content.heading || 'Notes'}
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
                      {currentReel.structured_content.items_or_markdown}
                    </ReactMarkdown>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Tab 2: Transcript with Clickable Timestamps */}
          {activeTab === 'transcript' && (
            <div className="text-[#1A1A1A] space-y-4">
              {timestampedTranscript.length > 0 ? (
                <div className="space-y-3">
                  {timestampedTranscript.map((seg, idx) => {
                    const isActive = videoTime >= seg.start && videoTime <= seg.end;
                    return (
                      <div
                        key={idx}
                        className={`flex items-start gap-3 p-2 rounded-[6px] transition-colors ${
                          isActive ? 'bg-[#E4ECE8]/60 border-l-2 border-[#2F5D50]' : 'hover:bg-[#F8F7F4]'
                        }`}
                      >
                        <button
                          onClick={() => handleSeek(seg.start)}
                          className="font-mono text-xs text-[#2F5D50] bg-[#F2F1EC] px-1.5 py-0.5 rounded shrink-0 hover:underline"
                        >
                          {formatSeconds(seg.start)}
                        </button>
                        <p className="text-[14px] leading-relaxed text-[#1A1A1A]">{seg.text}</p>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p className="text-[15px] leading-relaxed whitespace-pre-wrap font-normal">
                  {currentReel.transcript || 'No speech in video (music/silent).'}
                </p>
              )}
            </div>
          )}

          {/* Tab 3: Links & Resources with Verification Badges */}
          {activeTab === 'links' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs text-[#6B6B6B]">
                  {resources.length} extracted resource{resources.length === 1 ? '' : 's'}
                </span>
                <button
                  onClick={handleRecheckLinks}
                  disabled={recheckingLinks}
                  className="inline-flex items-center gap-1.5 text-xs text-[#2F5D50] hover:underline"
                >
                  <RefreshCw size={12} className={recheckingLinks ? 'animate-spin' : ''} />
                  <span>Re-check links</span>
                </button>
              </div>

              {resources.length === 0 ? (
                <p className="text-xs text-[#6B6B6B]">No links or tools detected in this reel.</p>
              ) : (
                <div className="divide-y divide-[#E8E6E1]">
                  {resources.map((res, i) => {
                    const hasUrl = res.url && res.url.trim().length > 0;
                    const finalTarget = res.final_url || res.url;
                    const statusVal = res.link_status || 'unverified';

                    return (
                      <div key={i} className="py-3 first:pt-0 last:pb-0">
                        <div className="flex items-baseline flex-wrap gap-2">
                          {hasUrl ? (
                            <a
                              href={finalTarget}
                              target="_blank"
                              rel="noreferrer"
                              className="text-[15px] font-medium text-[#2F5D50] hover:underline flex items-center gap-1"
                            >
                              <span>{res.name}</span>
                              <ExternalLink size={11} className="text-[#6B6B6B]" />
                            </a>
                          ) : (
                            <div className="flex items-center gap-2">
                              <span className="text-[15px] font-medium text-[#1A1A1A]">{res.name}</span>
                              <a
                                href={`https://www.google.com/search?q=${encodeURIComponent(res.name)}`}
                                target="_blank"
                                rel="noreferrer"
                                className="text-xs text-[#2F5D50] hover:underline inline-flex items-center gap-1"
                              >
                                <Search size={10} />
                                <span>Search the web</span>
                              </a>
                            </div>
                          )}

                          <span className="text-xs text-[#6B6B6B] capitalize">
                            · {res.type.replace('_', ' ')}
                          </span>

                          {hasUrl && (
                            <span
                              className={`text-[10px] px-1.5 py-0.2 rounded font-mono ${
                                statusVal === 'verified'
                                  ? 'bg-[#E4ECE8] text-[#2F5D50]'
                                  : statusVal === 'unreachable'
                                  ? 'bg-[#FAF0EE] text-[#B3382C]'
                                  : 'bg-[#F2F1EC] text-[#6B6B6B]'
                              }`}
                            >
                              {statusVal === 'verified' ? '✓ verified' : statusVal === 'unreachable' ? 'couldn\'t verify' : 'unverified'}
                            </span>
                          )}

                          {res.github_stars !== undefined && res.github_stars !== null && (
                            <span className="text-xs text-[#6B6B6B]">
                              ★ {res.github_stars.toLocaleString()}
                            </span>
                          )}
                        </div>

                        {res.description && (
                          <p className="text-xs text-[#6B6B6B] mt-1 leading-relaxed">
                            {res.description}
                          </p>
                        )}
                      </div>
                    );
                  })}
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
                  onDelete(currentReel.id);
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
