import React, { useState } from 'react';
import { X, Plus, Trash2, Edit2, Check, ArrowUp, ArrowDown } from 'lucide-react';

const MUTED_PALETTE = [
  '#4A6B82', // Slate blue
  '#477A64', // Forest green
  '#8A6D3B', // Muted ochre
  '#5C5885', // Soft indigo
  '#944E45', // Terracotta
  '#7B5C82', // Dusty plum
  '#8A526E', // Rosewood
  '#6B6B6B', // Neutral gray
];

export default function ManageCategoriesModal({ isOpen, onClose, categories, onSaveCategory, onUpdateCategory, onDeleteCategory, onReorderCategories }) {
  const [newCatName, setNewCatName] = useState('');
  const [newCatColor, setNewCatColor] = useState(MUTED_PALETTE[0]);
  const [editingId, setEditingId] = useState(null);
  const [editName, setEditName] = useState('');
  const [editColor, setEditColor] = useState('');
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!newCatName.trim()) return;
    setError('');
    try {
      await onSaveCategory(newCatName.trim(), newCatColor);
      setNewCatName('');
    } catch (err) {
      setError(err.message || 'Failed to add category');
    }
  };

  const startEdit = (cat) => {
    setEditingId(cat.id);
    setEditName(cat.name);
    setEditColor(cat.color || MUTED_PALETTE[0]);
    setError('');
  };

  const handleSaveEdit = async (catId) => {
    if (!editName.trim()) return;
    setError('');
    try {
      await onUpdateCategory(catId, { name: editName.trim(), color: editColor });
      setEditingId(null);
    } catch (err) {
      setError(err.message || 'Failed to update category');
    }
  };

  const handleMove = async (index, direction) => {
    const newIdx = index + direction;
    if (newIdx < 0 || newIdx >= categories.length) return;
    const reordered = [...categories];
    const temp = reordered[index];
    reordered[index] = reordered[newIdx];
    reordered[newIdx] = temp;
    await onReorderCategories(reordered.map((c) => c.id));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/35">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative z-10 w-full max-w-lg bg-[#FFFFFF] border border-[#E8E6E1] rounded-[12px] p-6 text-left shadow-none max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#E8E6E1]">
          <div>
            <h2 className="font-serif text-xl font-medium text-[#1A1A1A]">
              Manage categories
            </h2>
            <p className="text-xs text-[#6B6B6B] mt-0.5">
              Add, rename, reorder, or delete custom categories.
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-[8px] text-[#6B6B6B] hover:text-[#1A1A1A] transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {error && (
          <p className="text-xs text-[#B3382C] mt-3 leading-relaxed">
            {error}
          </p>
        )}

        {/* Categories List */}
        <div className="flex-1 overflow-y-auto py-3 space-y-2 divide-y divide-[#E8E6E1]">
          {categories.map((cat, idx) => {
            const isEditing = editingId === cat.id;
            const isOther = cat.id === 'other' || cat.is_default;

            return (
              <div key={cat.id} className="pt-2 first:pt-0 flex items-center justify-between gap-3 text-xs">
                {isEditing ? (
                  <div className="flex-1 flex items-center gap-2">
                    <input
                      type="text"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      className="flex-1 bg-[#FAFAF8] border border-[#E8E6E1] px-2.5 py-1.5 rounded-[8px] text-xs text-[#1A1A1A] focus:outline-none focus:border-[#2F5D50]"
                      autoFocus
                    />
                    <div className="flex items-center gap-1">
                      {MUTED_PALETTE.map((c) => (
                        <button
                          key={c}
                          type="button"
                          onClick={() => setEditColor(c)}
                          className={`w-4 h-4 rounded-full border ${editColor === c ? 'border-[#1A1A1A] ring-1 ring-[#1A1A1A]' : 'border-transparent'}`}
                          style={{ backgroundColor: c }}
                        />
                      ))}
                    </div>
                    <button
                      onClick={() => handleSaveEdit(cat.id)}
                      className="p-1.5 rounded-[8px] bg-[#2F5D50] text-[#FAFAF8] hover:bg-[#254A40]"
                      title="Save"
                    >
                      <Check size={13} />
                    </button>
                    <button
                      onClick={() => setEditingId(null)}
                      className="p-1.5 rounded-[8px] border border-[#E8E6E1] text-[#6B6B6B] hover:text-[#1A1A1A]"
                      title="Cancel"
                    >
                      <X size={13} />
                    </button>
                  </div>
                ) : (
                  <>
                    <div className="flex items-center gap-2.5 truncate">
                      <span
                        className="w-3 h-3 rounded-full shrink-0"
                        style={{ backgroundColor: cat.color || '#6B6B6B' }}
                      />
                      <span className="font-normal text-[#1A1A1A] truncate">{cat.name}</span>
                      <span className="text-[11px] text-[#6B6B6B]">
                        ({cat.reel_count || 0})
                      </span>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => handleMove(idx, -1)}
                        disabled={idx === 0}
                        title="Move up"
                        className="p-1 text-[#6B6B6B] hover:text-[#1A1A1A] disabled:opacity-20"
                      >
                        <ArrowUp size={13} />
                      </button>
                      <button
                        onClick={() => handleMove(idx, 1)}
                        disabled={idx === categories.length - 1}
                        title="Move down"
                        className="p-1 text-[#6B6B6B] hover:text-[#1A1A1A] disabled:opacity-20"
                      >
                        <ArrowDown size={13} />
                      </button>

                      {!isOther && (
                        <>
                          <button
                            onClick={() => startEdit(cat)}
                            title="Rename & recolor"
                            className="p-1 text-[#6B6B6B] hover:text-[#1A1A1A]"
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            onClick={() => onDeleteCategory(cat.id, cat.name)}
                            title="Delete category (moves reels to Other)"
                            className="p-1 text-[#6B6B6B] hover:text-[#B3382C]"
                          >
                            <Trash2 size={13} />
                          </button>
                        </>
                      )}
                    </div>
                  </>
                )}
              </div>
            );
          })}
        </div>

        {/* Add Category Form */}
        <form onSubmit={handleAdd} className="pt-4 border-t border-[#E8E6E1] mt-2 space-y-2">
          <p className="text-xs font-normal text-[#6B6B6B]">Add new category</p>
          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="Category name (e.g. Design, Cooking)..."
              value={newCatName}
              onChange={(e) => setNewCatName(e.target.value)}
              className="flex-1 bg-[#FAFAF8] border border-[#E8E6E1] px-3 py-2 rounded-[8px] text-xs text-[#1A1A1A] placeholder-[#6B6B6B] focus:outline-none focus:border-[#2F5D50]"
            />
            <button
              type="submit"
              disabled={!newCatName.trim()}
              className="px-3.5 py-2 rounded-[8px] bg-[#2F5D50] hover:bg-[#254A40] text-[#FAFAF8] text-xs font-medium flex items-center gap-1 disabled:opacity-40 transition-colors"
            >
              <Plus size={13} />
              <span>Add</span>
            </button>
          </div>
          <div className="flex items-center gap-1.5 pt-1">
            <span className="text-[11px] text-[#6B6B6B] mr-1">Color:</span>
            {MUTED_PALETTE.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setNewCatColor(c)}
                className={`w-4 h-4 rounded-full border ${newCatColor === c ? 'border-[#1A1A1A] ring-1 ring-[#1A1A1A]' : 'border-transparent'}`}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>
        </form>
      </div>
    </div>
  );
}
