import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Loader2, Plus, X, RefreshCw, SlidersHorizontal, Trash2 } from 'lucide-react';
import Header from './components/Header';
import ReelCard from './components/ReelCard';
import ReelListRow from './components/ReelListRow';
import ReelCompactRow from './components/ReelCompactRow';
import ReelDetailView from './components/ReelDetailView';
import SaveReelModal from './components/SaveReelModal';
import PasscodeScreen from './components/PasscodeScreen';
import BrandPreviewPage from './components/BrandPreviewPage';
import ManageCategoriesModal from './components/ManageCategoriesModal';
import SettingsModal from './components/SettingsModal';

const STATUS_FILTERS = ['All', 'Unread', 'Reviewed', 'Tried', 'Archived'];
const ITEMS_PER_PAGE = 50;

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authChecked, setAuthChecked] = useState(false);

  const [allReels, setAllReels] = useState([]);
  const [categories, setCategories] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [selectedStatus, setSelectedStatus] = useState('All');
  const [isManageCatOpen, setIsManageCatOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState(null);

  // Auto-mark reviewed setting with localStorage persistence (default true)
  const [autoMarkReviewed, setAutoMarkReviewed] = useState(() => {
    try {
      const saved = localStorage.getItem('savyor_auto_mark_reviewed');
      if (saved !== null) return saved === 'true';
    } catch (_) {}
    return true;
  });

  const handleToggleAutoMarkReviewed = useCallback((val) => {
    setAutoMarkReviewed(val);
    try {
      localStorage.setItem('savyor_auto_mark_reviewed', String(val));
    } catch (_) {}
  }, []);

  // View state with localStorage persistence & default: list on < 640px, else grid
  const [view, setView] = useState(() => {
    try {
      const saved = localStorage.getItem('savyor_view');
      if (saved && ['grid', 'list', 'compact'].includes(saved)) return saved;
    } catch (_) {}
    return typeof window !== 'undefined' && window.innerWidth < 640 ? 'list' : 'grid';
  });

  // Sort state with localStorage persistence: newest, oldest, title, reviewed
  const [sort, setSort] = useState(() => {
    try {
      const saved = localStorage.getItem('savyor_sort');
      if (saved && ['newest', 'oldest', 'title', 'reviewed'].includes(saved)) return saved;
    } catch (_) {}
    return 'newest';
  });

  // Keyboard navigation focus index
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const [page, setPage] = useState(1);

  // Route state: { page: 'home' | 'detail' | 'brand', id?: string }
  const [currentRoute, setCurrentRoute] = useState(() => {
    if (window.location.pathname === '/brand') return { page: 'brand' };
    const match = window.location.pathname.match(/^\/reel\/([A-Za-z0-9_-]+)/);
    if (match) return { page: 'detail', id: match[1] };
    return { page: 'home' };
  });

  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [sharedPrefillUrl, setSharedPrefillUrl] = useState('');

  // Persist View
  const handleViewChange = useCallback((newView) => {
    setView(newView);
    setFocusedIndex(-1);
    try {
      localStorage.setItem('savyor_view', newView);
    } catch (_) {}
  }, []);

  // Persist Sort
  const handleSortChange = useCallback((newSort) => {
    setSort(newSort);
    setFocusedIndex(-1);
    try {
      localStorage.setItem('savyor_sort', newSort);
    } catch (_) {}
  }, []);

  // Sync browser back/forward navigation
  useEffect(() => {
    const handlePopState = () => {
      if (window.location.pathname === '/brand') {
        setCurrentRoute({ page: 'brand' });
      } else {
        const match = window.location.pathname.match(/^\/reel\/([A-Za-z0-9_-]+)/);
        if (match) {
          setCurrentRoute({ page: 'detail', id: match[1] });
        } else {
          setCurrentRoute({ page: 'home' });
        }
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const apiFetch = useCallback(
    (url, options = {}) => {
      const activeToken = localStorage.getItem('savyor_passcode') || localStorage.getItem('reelvault_token') || '';
      const headers = {
        'X-API-Token': activeToken,
        ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
        ...(options.headers || {}),
      };
      return fetch(url, { ...options, headers, credentials: 'include' });
    },
    []
  );

  // Fetch categories from DB
  const fetchCategories = useCallback(async () => {
    try {
      const res = await apiFetch('/api/categories');
      if (res.ok) {
        const data = await res.json();
        setCategories(data);
      }
    } catch (err) {
      console.error('Failed to fetch categories:', err);
    }
  }, [apiFetch]);

  // Fetch all reels from database
  const fetchAllReels = useCallback(async () => {
    try {
      setLoading(true);
      const res = await apiFetch('/api/reels?status=all');
      if (res.status === 401) {
        setIsAuthenticated(false);
        localStorage.removeItem('savyor_passcode');
        localStorage.removeItem('reelvault_token');
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setAllReels(data);
        setIsAuthenticated(true);
      }
    } catch (err) {
      console.error('Failed to fetch reels:', err);
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  // Check and establish authentic session on app mount
  useEffect(() => {
    let isMounted = true;
    fetch('/api/auth/status', { credentials: 'include' })
      .then((res) => res.json())
      .then(async (data) => {
        if (!isMounted) return;
        if (!data.auth_required) {
          setIsAuthenticated(true);
        } else if (data.is_authenticated) {
          setIsAuthenticated(true);
        } else {
          // If server requires auth and cookie is missing, check if localStorage has passcode
          const storedToken = localStorage.getItem('savyor_passcode') || localStorage.getItem('reelvault_token');
          if (storedToken) {
            try {
              const loginRes = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ passcode: storedToken }),
              });
              if (loginRes.ok) {
                if (isMounted) setIsAuthenticated(true);
                return;
              }
            } catch (_) {}
            localStorage.removeItem('savyor_passcode');
            localStorage.removeItem('reelvault_token');
          }
          if (isMounted) setIsAuthenticated(false);
        }
      })
      .catch(() => {
        if (isMounted) setIsAuthenticated(false);
      })
      .finally(() => {
        if (isMounted) setAuthChecked(true);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  // Initial load once authenticated
  useEffect(() => {
    if (isAuthenticated) {
      fetchCategories();
      fetchAllReels();
    }
  }, [isAuthenticated, fetchCategories, fetchAllReels]);

  // Handle URL share targets (GET /?url=... or /?text=...)
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const sharedUrl = params.get('url');
    const sharedText = params.get('text');
    const target = sharedUrl || sharedText;
    if (target) {
      setSharedPrefillUrl(target);
      window.history.replaceState({}, '', window.location.pathname);
      setIsAddModalOpen(true);
    }
  }, []);

  // Poll active background jobs
  useEffect(() => {
    const activeJobs = jobs.filter((j) => j.status === 'queued' || j.status === 'downloading' || j.status === 'analyzing');
    if (activeJobs.length === 0) return;

    const interval = setInterval(async () => {
      for (const job of activeJobs) {
        try {
          const res = await apiFetch(`/api/jobs/${job.id}`);
          if (res.ok) {
            const data = await res.json();
            setJobs((prev) =>
              prev.map((j) => (j.id === job.id ? { ...j, ...data } : j))
            );

            if (data.status === 'done') {
              fetchAllReels();
              fetchCategories();
            }
          }
        } catch (err) {
          console.error(`Error polling job ${job.id}:`, err);
        }
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [jobs, apiFetch, fetchAllReels, fetchCategories]);

  // Queue a new reel
  const handleSaveReel = async (url) => {
    const res = await apiFetch('/api/reels', {
      method: 'POST',
      body: JSON.stringify({ url }),
    });

    if (res.status === 401) {
      setIsAuthenticated(false);
      throw new Error('Vault is locked. Please unlock first.');
    }

    if (res.status === 409) {
      const err = await res.json();
      throw new Error(err.detail || 'This reel is already in your vault.');
    }

    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || 'Failed to save reel.');
    }

    const data = await res.json();
    setJobs((prev) => [
      {
        id: data.job_id,
        url,
        status: data.status || 'queued',
        progress: 'Queued for processing...',
      },
      ...prev,
    ]);

    if (data.is_existing) {
      fetchAllReels();
    }
  };

  // Upload a video directly
  const handleUploadVideo = async (file) => {
    const formData = new FormData();
    formData.append('file', file);

    const res = await apiFetch('/api/reels/upload', {
      method: 'POST',
      body: formData,
    });

    if (res.status === 401) {
      setIsAuthenticated(false);
      throw new Error('Vault is locked. Please unlock first.');
    }

    if (res.status === 413) {
      throw new Error('Video file exceeds 100 MB limit.');
    }

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.detail || 'Failed to upload video.');
    }

    const data = await res.json();
    setJobs((prev) => [
      {
        id: data.job_id,
        url: file.name,
        status: data.status || 'queued',
        progress: 'Queued for processing...',
      },
      ...prev,
    ]);
  };

  const handleRetryJob = async (job) => {
    setJobs((prev) => prev.filter((j) => j.id !== job.id));
    if (job.url && !job.url.startsWith('upload://') && job.url.includes('instagram.com/')) {
      await handleSaveReel(job.url);
    }
  };

  const handleDismissJob = (jobId) => {
    setJobs((prev) => prev.filter((j) => j.id !== jobId));
  };

  // Reel update actions
  const handleUpdateStatus = async (reelId, newStatus) => {
    try {
      const res = await apiFetch(`/api/reels/${reelId}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: newStatus }),
      });
      if (res.ok) {
        const updated = await res.json();
        setAllReels((prev) => prev.map((r) => (r.id === reelId ? updated : r)));
      }
    } catch (err) {
      console.error('Failed to update status:', err);
    }
  };

  const handleUpdateCategory = async (reelId, categoryId, categoryName) => {
    try {
      const res = await apiFetch(`/api/reels/${reelId}`, {
        method: 'PATCH',
        body: JSON.stringify({ category_id: categoryId }),
      });
      if (res.ok) {
        const updated = await res.json();
        setAllReels((prev) => prev.map((r) => (r.id === reelId ? updated : r)));
        fetchCategories();
      }
    } catch (err) {
      console.error('Failed to update category:', err);
    }
  };

  const handleUpdateTags = async (reelId, newTags) => {
    try {
      const res = await apiFetch(`/api/reels/${reelId}`, {
        method: 'PATCH',
        body: JSON.stringify({ tags: newTags }),
      });
      if (res.ok) {
        const updated = await res.json();
        setAllReels((prev) => prev.map((r) => (r.id === reelId ? updated : r)));
      }
    } catch (err) {
      console.error('Failed to update tags:', err);
    }
  };

  const handleAcceptSuggestedCategory = async (reelId, suggestedName) => {
    try {
      const resCat = await apiFetch('/api/categories', {
        method: 'POST',
        body: JSON.stringify({ name: suggestedName }),
      });
      if (!resCat.ok) {
        const err = await resCat.json();
        throw new Error(err.detail || 'Failed to create category');
      }
      const newCat = await resCat.json();
      await fetchCategories();

      const resReel = await apiFetch(`/api/reels/${reelId}`, {
        method: 'PATCH',
        body: JSON.stringify({ category_id: newCat.id, suggested_new_category: null }),
      });
      if (resReel.ok) {
        const updated = await resReel.json();
        setAllReels((prev) => prev.map((r) => (r.id === reelId ? updated : r)));
      }
    } catch (err) {
      console.error('Failed to accept suggested category:', err);
    }
  };

  const handleDismissSuggestedCategory = async (reelId) => {
    try {
      const res = await apiFetch(`/api/reels/${reelId}`, {
        method: 'PATCH',
        body: JSON.stringify({ suggested_new_category: null }),
      });
      if (res.ok) {
        const updated = await res.json();
        setAllReels((prev) => prev.map((r) => (r.id === reelId ? updated : r)));
      }
    } catch (err) {
      console.error('Failed to dismiss suggestion:', err);
    }
  };

  // Category management handlers
  const handleSaveCategory = async (name, color) => {
    const res = await apiFetch('/api/categories', {
      method: 'POST',
      body: JSON.stringify({ name, color }),
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || 'Failed to create category');
    }
    await fetchCategories();
  };

  const handleUpdateCategoryItem = async (catId, payload) => {
    const res = await apiFetch(`/api/categories/${catId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || 'Failed to update category');
    }
    await fetchCategories();
    await fetchAllReels();
  };

  const handleDeleteCategoryItem = async (catId, catName) => {
    if (!window.confirm(`Delete "${catName}"? All reels in this category will be moved to "Other".`)) {
      return;
    }
    const res = await apiFetch(`/api/categories/${catId}`, {
      method: 'DELETE',
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || 'Failed to delete category');
    }
    await fetchCategories();
    await fetchAllReels();
    if (selectedCategory === catName) {
      setSelectedCategory('All');
    }
  };

  const handleReorderCategories = async (categoryIds) => {
    const res = await apiFetch('/api/categories/reorder', {
      method: 'POST',
      body: JSON.stringify({ category_ids: categoryIds }),
    });
    if (res.ok) {
      await fetchCategories();
    }
  };

  // Navigation handlers
  const navigateToReel = (id) => {
    window.history.pushState({}, '', `/reel/${id}`);
    setCurrentRoute({ page: 'detail', id });
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const navigateHome = () => {
    window.history.pushState({}, '', '/');
    setCurrentRoute({ page: 'home' });
  };

  const navigateToBrand = () => {
    window.history.pushState({}, '', '/brand');
    setCurrentRoute({ page: 'brand' });
  };

  const handleDeleteReel = async (id) => {
    try {
      await apiFetch(`/api/reels/${id}`, { method: 'DELETE' });
      setAllReels((prev) => prev.filter((r) => r.id !== id));
      setDeleteConfirmId(null);
      if (currentRoute.page === 'detail') {
        navigateHome();
      }
    } catch (err) {
      console.error('Failed to delete reel:', err);
    }
  };

  const handleUnlock = (token) => {
    if (token) localStorage.setItem('savyor_passcode', token);
    setIsAuthenticated(true);
    fetchCategories();
    fetchAllReels();
  };

  const handleLock = async () => {
    try {
      await fetch('/api/logout', { method: 'POST', credentials: 'include' });
    } catch (_) {}
    localStorage.removeItem('savyor_passcode');
    localStorage.removeItem('reelvault_token');
    setIsAuthenticated(false);
  };

  // Visible categories in bar: 'All' + non-empty categories
  const visibleCategories = useMemo(() => {
    const list = ['All'];
    categories.forEach((cat) => {
      const count = allReels.filter((r) => r.category_id === cat.id || r.category === cat.name).length;
      if (count > 0) {
        list.push(cat.name);
      }
    });
    return list;
  }, [categories, allReels]);

  // Filtered reels based on search, category tab, and status filter
  const displayedReels = useMemo(() => {
    return allReels.filter((reel) => {
      const reelStatus = reel.status || 'unread';
      if (selectedStatus === 'All') {
        if (reelStatus === 'archived') return false;
      } else {
        if (reelStatus.toLowerCase() !== selectedStatus.toLowerCase()) return false;
      }

      if (selectedCategory !== 'All' && reel.category !== selectedCategory) {
        return false;
      }

      if (search.trim()) {
        const q = search.toLowerCase();
        const inTitle = reel.title?.toLowerCase().includes(q);
        const inSummary = reel.summary?.toLowerCase().includes(q);
        const inCreator = reel.media?.uploader?.toLowerCase().includes(q) || reel.media?.channel?.toLowerCase().includes(q);
        const inTags = (reel.tags || []).some((t) => t.toLowerCase().includes(q));
        const inTranscript = reel.transcript?.toLowerCase().includes(q);
        const inContent = reel.structured_content?.items_or_markdown?.toLowerCase().includes(q);
        const inTakeaways = (reel.key_takeaways || []).some((t) => t.toLowerCase().includes(q));
        if (!inTitle && !inSummary && !inCreator && !inTags && !inTranscript && !inContent && !inTakeaways) {
          return false;
        }
      }

      return true;
    });
  }, [allReels, selectedCategory, selectedStatus, search]);

  // Sort displayed reels
  const sortedReels = useMemo(() => {
    const list = [...displayedReels];
    if (sort === 'oldest') {
      list.sort((a, b) => new Date(a.created_at || 0) - new Date(b.created_at || 0));
    } else if (sort === 'title') {
      list.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
    } else if (sort === 'reviewed') {
      list.sort((a, b) => {
        const timeA = a.reviewed_at ? new Date(a.reviewed_at).getTime() : 0;
        const timeB = b.reviewed_at ? new Date(b.reviewed_at).getTime() : 0;
        if (timeA && timeB) return timeB - timeA;
        if (timeA) return -1;
        if (timeB) return 1;
        return new Date(b.created_at || 0) - new Date(a.created_at || 0);
      });
    } else {
      // newest (default)
      list.sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0));
    }
    return list;
  }, [displayedReels, sort]);

  // Paginated reels when > 200 items
  const totalPages = Math.ceil(sortedReels.length / ITEMS_PER_PAGE);
  const pagedReels = useMemo(() => {
    if (sortedReels.length <= 200) return sortedReels;
    const start = (page - 1) * ITEMS_PER_PAGE;
    return sortedReels.slice(start, start + ITEMS_PER_PAGE);
  }, [sortedReels, page]);

  // Reset page when filter/search/sort changes
  useEffect(() => {
    setPage(1);
    setFocusedIndex(-1);
  }, [search, selectedCategory, selectedStatus, sort]);

  // Keyboard shortcuts (g, l, c, j, k, ArrowDown, ArrowUp, Enter)
  useEffect(() => {
    if (currentRoute.page !== 'home') return;

    const handleKeyDown = (e) => {
      // Ignore when focused in input, textarea, or select
      const tag = document.activeElement?.tagName;
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag) || document.activeElement?.isContentEditable) {
        return;
      }

      if (e.key === 'g' || e.key === 'G') {
        e.preventDefault();
        handleViewChange('grid');
      } else if (e.key === 'l' || e.key === 'L') {
        e.preventDefault();
        handleViewChange('list');
      } else if (e.key === 'c' || e.key === 'C') {
        e.preventDefault();
        handleViewChange('compact');
      } else if (e.key === 'j' || e.key === 'ArrowDown') {
        e.preventDefault();
        setFocusedIndex((prev) => Math.min(pagedReels.length - 1, prev < 0 ? 0 : prev + 1));
      } else if (e.key === 'k' || e.key === 'ArrowUp') {
        e.preventDefault();
        setFocusedIndex((prev) => Math.max(0, prev - 1));
      } else if (e.key === 'Enter') {
        if (focusedIndex >= 0 && focusedIndex < pagedReels.length) {
          e.preventDefault();
          navigateToReel(pagedReels[focusedIndex].id);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [currentRoute.page, pagedReels, focusedIndex, handleViewChange]);

  const currentReel = useMemo(() => {
    if (currentRoute.page === 'detail' && currentRoute.id) {
      return allReels.find((r) => r.id === currentRoute.id);
    }
    return null;
  }, [allReels, currentRoute]);

  // Show Dev Brand Asset Preview Page (/brand)
  if (currentRoute.page === 'brand') {
    if (!import.meta.env.DEV && !isAuthenticated) {
      return <PasscodeScreen onUnlock={handleUnlock} />;
    }
    return <BrandPreviewPage onBack={navigateHome} />;
  }

  // Wait for initial auth check
  if (!authChecked) {
    return (
      <div className="min-h-screen bg-[#FAFAF8] flex items-center justify-center">
        <Loader2 size={24} className="animate-spin text-[#2F5D50]" />
      </div>
    );
  }

  // Passcode Lock Screen
  if (!isAuthenticated) {
    return <PasscodeScreen onUnlock={handleUnlock} />;
  }

  return (
    <div className="min-h-screen bg-[#FAFAF8] text-[#1A1A1A] flex flex-col font-sans">
      {/* Header with Search, View Switcher & Sort Toolbar */}
      <Header
        search={search}
        onSearchChange={setSearch}
        view={view}
        onViewChange={handleViewChange}
        sort={sort}
        onSortChange={handleSortChange}
        onOpenAdd={() => setIsAddModalOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onNavigateHome={navigateHome}
        onNavigateBrand={navigateToBrand}
        onLockVault={handleLock}
        hasToken={true}
      />

      {/* Main Content Area */}
      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
        {currentRoute.page === 'detail' && currentReel ? (
          /* Full Page Detail View (/reel/:id) */
          <ReelDetailView
            reel={currentReel}
            onBack={navigateHome}
            onDelete={handleDeleteReel}
            onUpdateStatus={handleUpdateStatus}
            onUpdateCategory={handleUpdateCategory}
            onUpdateTags={handleUpdateTags}
            onAcceptSuggestedCategory={handleAcceptSuggestedCategory}
            onDismissSuggestedCategory={handleDismissSuggestedCategory}
            categories={categories}
            autoMarkReviewed={autoMarkReviewed}
          />
        ) : (
          /* Home Library View */
          <>
            {/* Category Bar & Status Filter */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#E8E6E1] mb-8 pb-1">
              {/* Plain Underlined Category Tabs */}
              <div className="flex items-center gap-6 overflow-x-auto text-xs no-scrollbar">
                {visibleCategories.map((cat) => {
                  const isActive = selectedCategory === cat;
                  return (
                    <button
                      key={cat}
                      onClick={() => setSelectedCategory(cat)}
                      className={`pb-2 whitespace-nowrap transition-colors border-b-2 ${
                        isActive
                          ? 'border-[#2F5D50] text-[#2F5D50] font-medium'
                          : 'border-transparent text-[#6B6B6B] hover:text-[#1A1A1A] font-normal'
                      }`}
                    >
                      {cat}
                    </button>
                  );
                })}

                <button
                  onClick={() => setIsManageCatOpen(true)}
                  className="pb-2 text-xs text-[#6B6B6B] hover:text-[#1A1A1A] flex items-center gap-1 font-normal whitespace-nowrap"
                  title="Manage categories"
                >
                  <SlidersHorizontal size={12} />
                  <span>Manage</span>
                </button>
              </div>

              {/* Status Filter Pill Bar */}
              <div className="flex items-center gap-1 self-start sm:self-auto text-xs pb-1 sm:pb-0 flex-wrap">
                <span className="text-[#6B6B6B] text-[11px] mr-1">Status:</span>
                {STATUS_FILTERS.map((st) => (
                  <button
                    key={st}
                    onClick={() => setSelectedStatus(st)}
                    className={`min-h-[40px] sm:min-h-0 flex items-center px-2.5 py-1 sm:py-0.5 rounded-[6px] text-[11px] transition-colors ${
                      selectedStatus === st
                        ? 'bg-[#2F5D50] text-[#FAFAF8] font-medium'
                        : 'text-[#6B6B6B] hover:text-[#1A1A1A] bg-transparent'
                    }`}
                  >
                    {st}
                  </button>
                ))}
              </div>
            </div>

            {/* Skeletons Matching Selected View */}
            {loading && allReels.length === 0 ? (
              view === 'grid' ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                  {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
                    <div key={n} className="flex flex-col text-left">
                      <div
                        className="w-full calm-skeleton rounded-[12px] border border-[#E8E6E1]"
                        style={{ aspectRatio: '9/16' }}
                      />
                      <div className="calm-skeleton h-4 w-4/5 rounded mt-3" />
                      <div className="calm-skeleton h-3 w-1/2 rounded mt-2" />
                    </div>
                  ))}
                </div>
              ) : view === 'list' ? (
                <div className="divide-y divide-[#E8E6E1]">
                  {[1, 2, 3, 4, 5, 6].map((n) => (
                    <div key={n} className="py-4 flex items-start gap-4 text-left">
                      <div className="w-[56px] h-[100px] calm-skeleton rounded-[8px] shrink-0" />
                      <div className="flex-1 space-y-2 pt-1">
                        <div className="calm-skeleton h-4 w-3/4 rounded" />
                        <div className="calm-skeleton h-3 w-full rounded" />
                        <div className="calm-skeleton h-3 w-1/3 rounded mt-2" />
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="divide-y divide-[#E8E6E1]">
                  {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => (
                    <div key={n} className="h-11 flex items-center justify-between gap-4 px-2">
                      <div className="flex items-center gap-3 flex-1">
                        <div className="w-2 h-2 rounded-full calm-skeleton shrink-0" />
                        <div className="calm-skeleton h-3.5 w-1/2 rounded" />
                        <div className="calm-skeleton h-3 w-20 rounded" />
                      </div>
                      <div className="calm-skeleton h-3 w-16 rounded" />
                    </div>
                  ))}
                </div>
              )
            ) : allReels.length === 0 && jobs.length === 0 ? (
              /* Friendly Empty State */
              <div className="py-24 text-center max-w-sm mx-auto">
                <img src="/logo-mark.svg" alt="Savyor" className="w-6 h-6 text-[#1A1A1A] mx-auto mb-3 opacity-30" />
                <h3 className="font-serif text-xl font-normal text-[#1A1A1A] mb-1">
                  No reels saved yet
                </h3>
                <p className="text-xs text-[#6B6B6B] mb-6">
                  Save the reel. Keep the idea.
                </p>
                <button
                  onClick={() => setIsAddModalOpen(true)}
                  className="px-4 py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-xs font-medium transition-colors"
                >
                  Add your first reel
                </button>
              </div>
            ) : (
              /* Library View Content */
              <div className="space-y-4">
                {/* Active & Failed Jobs for List & Compact */}
                {view !== 'grid' && jobs.length > 0 && (
                  <div className="space-y-2 mb-4">
                    {jobs.map((job) => (
                      <div
                        key={job.id}
                        className={`p-3 rounded-[8px] border text-xs flex items-center justify-between gap-3 text-left ${
                          job.status === 'failed'
                            ? 'border-red-200 bg-red-50/50'
                            : 'border-[#E8E6E1] bg-[#FFFFFF]'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          {job.status === 'failed' ? (
                            <span className="text-[#B3382C] font-medium">Failed:</span>
                          ) : (
                            <Loader2 size={13} className="animate-spin text-[#2F5D50] shrink-0" />
                          )}
                          <span className="text-[#1A1A1A] truncate">
                            {job.error || job.progress || 'Processing reel...'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          {job.status === 'failed' && (
                            <button
                              onClick={() => handleRetryJob(job)}
                              className="px-2 py-1 rounded-[6px] border border-[#E8E6E1] text-[#1A1A1A] hover:bg-[#FAFAF8] flex items-center gap-1"
                            >
                              <RefreshCw size={11} />
                              <span>Retry</span>
                            </button>
                          )}
                          <button
                            onClick={() => handleDismissJob(job.id)}
                            className="text-[#6B6B6B] hover:text-[#1A1A1A] p-1"
                          >
                            <X size={13} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* No search results */}
                {sortedReels.length === 0 && search.trim() && (
                  <div className="py-20 text-center max-w-sm mx-auto">
                    <p className="font-serif text-lg text-[#1A1A1A] mb-1">
                      No reels found for "{search}"
                    </p>
                    <p className="text-xs text-[#6B6B6B] mb-4">
                      Try searching another creator, keyword, or note phrase.
                    </p>
                    <button
                      onClick={() => setSearch('')}
                      className="text-xs text-[#2F5D50] hover:underline"
                    >
                      Clear search
                    </button>
                  </div>
                )}

                {/* VIEW 1: GRID */}
                {view === 'grid' && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                    {/* Active Jobs in Grid */}
                    {jobs.map((job) => {
                      if (job.status === 'failed') {
                        return (
                          <div
                            key={job.id}
                            className="rounded-[12px] border border-red-200 bg-[#FFFFFF] p-4 flex flex-col justify-between text-left text-xs"
                          >
                            <div>
                              <div className="flex items-center justify-between mb-2">
                                <span className="text-[#B3382C] font-medium">Failed to save</span>
                                <button
                                  onClick={() => handleDismissJob(job.id)}
                                  className="text-[#6B6B6B] hover:text-[#1A1A1A]"
                                >
                                  <X size={13} />
                                </button>
                              </div>
                              <p className="text-[#6B6B6B] leading-relaxed mb-2 line-clamp-3">
                                {job.error || 'Failed to download or analyze reel.'}
                              </p>
                            </div>
                            <div className="pt-3 border-t border-[#E8E6E1] flex justify-end">
                              <button
                                onClick={() => handleRetryJob(job)}
                                className="px-2.5 py-1 rounded-[8px] border border-[#E8E6E1] hover:bg-[#FAFAF8] text-[#1A1A1A] flex items-center gap-1 font-normal"
                              >
                                <RefreshCw size={11} />
                                <span>Retry</span>
                              </button>
                            </div>
                          </div>
                        );
                      }

                      return (
                        <div
                          key={job.id}
                          className="rounded-[12px] border border-[#E8E6E1] bg-[#FFFFFF] p-4 flex flex-col justify-between text-left"
                          style={{ aspectRatio: '9/16' }}
                        >
                          <div className="flex items-center justify-between text-xs text-[#6B6B6B]">
                            <span className="capitalize">{job.status}...</span>
                            <Loader2 size={13} className="animate-spin text-[#2F5D50]" />
                          </div>

                          <div className="my-auto text-center px-2">
                            <img src="/logo-mark.svg" alt="Savyor" className="w-5 h-5 text-[#2F5D50] mx-auto mb-2 opacity-60" />
                            <p className="font-serif text-sm font-medium text-[#1A1A1A]">
                              Extracting notes
                            </p>
                            <p className="text-xs text-[#6B6B6B] mt-1 line-clamp-2">
                              {job.progress || 'Processing video stream...'}
                            </p>
                          </div>

                          <div className="w-full bg-[#E8E6E1] h-1 rounded overflow-hidden">
                            <div
                              className="bg-[#2F5D50] h-full transition-all duration-300"
                              style={{
                                width:
                                  job.status === 'downloading'
                                    ? '45%'
                                    : job.status === 'analyzing'
                                    ? '85%'
                                    : '20%',
                              }}
                            />
                          </div>
                        </div>
                      );
                    })}

                    {/* Reel Cards */}
                    {pagedReels.map((reel, idx) => (
                      <ReelCard
                        key={reel.id}
                        reel={reel}
                        isFocused={focusedIndex === idx}
                        onClick={() => navigateToReel(reel.id)}
                      />
                    ))}
                  </div>
                )}

                {/* VIEW 2: LIST */}
                {view === 'list' && (
                  <div className={jobs.length > 0 ? 'border-t border-[#E8E6E1]' : ''}>
                    {pagedReels.map((reel, idx) => (
                      <ReelListRow
                        key={reel.id}
                        reel={reel}
                        isFocused={focusedIndex === idx}
                        searchQuery={search}
                        categories={categories}
                        onClick={() => navigateToReel(reel.id)}
                        onUpdateStatus={handleUpdateStatus}
                        onUpdateCategory={handleUpdateCategory}
                        onDelete={(id) => setDeleteConfirmId(id)}
                      />
                    ))}
                  </div>
                )}

                {/* VIEW 3: COMPACT */}
                {view === 'compact' && (
                  <div className={jobs.length > 0 ? 'border-t border-[#E8E6E1]' : ''}>
                    {pagedReels.map((reel, idx) => (
                      <ReelCompactRow
                        key={reel.id}
                        reel={reel}
                        isFocused={focusedIndex === idx}
                        categories={categories}
                        onClick={() => navigateToReel(reel.id)}
                        onUpdateStatus={handleUpdateStatus}
                        onUpdateCategory={handleUpdateCategory}
                        onDelete={(id) => setDeleteConfirmId(id)}
                      />
                    ))}
                  </div>
                )}

                {/* Pagination (when > 200 reels) */}
                {sortedReels.length > 200 && (
                  <div className="flex items-center justify-between border-t border-[#E8E6E1] pt-4 mt-6 text-xs text-[#6B6B6B]">
                    <span>
                      Showing {(page - 1) * ITEMS_PER_PAGE + 1}–{Math.min(page * ITEMS_PER_PAGE, sortedReels.length)} of {sortedReels.length} reels
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setPage((p) => Math.max(1, p - 1))}
                        disabled={page === 1}
                        className="px-2.5 py-1 rounded-[6px] border border-[#E8E6E1] disabled:opacity-30 hover:bg-[#F2F0EB] transition-colors"
                      >
                        Previous
                      </button>
                      <span className="px-1">
                        Page {page} of {totalPages}
                      </span>
                      <button
                        onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                        disabled={page === totalPages}
                        className="px-2.5 py-1 rounded-[6px] border border-[#E8E6E1] disabled:opacity-30 hover:bg-[#F2F0EB] transition-colors"
                      >
                        Next
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </main>

      {/* Add Reel Modal */}
      <SaveReelModal
        isOpen={isAddModalOpen}
        initialUrl={sharedPrefillUrl}
        onClose={() => {
          setIsAddModalOpen(false);
          setSharedPrefillUrl('');
        }}
        onSave={handleSaveReel}
        onUpload={handleUploadVideo}
      />

      {/* Manage Categories Modal */}
      <ManageCategoriesModal
        isOpen={isManageCatOpen}
        onClose={() => setIsManageCatOpen(false)}
        categories={categories}
        onSaveCategory={handleSaveCategory}
        onUpdateCategory={handleUpdateCategoryItem}
        onDeleteCategory={handleDeleteCategoryItem}
        onReorderCategories={handleReorderCategories}
      />

      {/* Settings Modal */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        autoMarkReviewed={autoMarkReviewed}
        onToggleAutoMarkReviewed={handleToggleAutoMarkReviewed}
      />

      {/* Delete Confirmation Dialog */}
      {deleteConfirmId && (
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
                onClick={() => setDeleteConfirmId(null)}
                className="px-3.5 py-2 rounded-[8px] border border-[#E8E6E1] text-[#1A1A1A] hover:bg-[#FAFAF8] text-xs font-normal transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleDeleteReel(deleteConfirmId)}
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
