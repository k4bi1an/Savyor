import React from 'react';
import { ArrowLeft, Download, ExternalLink } from 'lucide-react';

export default function BrandPreviewPage({ onBack }) {
  const assets = [
    { name: 'logo-mark.svg', type: 'SVG', desc: 'Bookmark with play-triangle cutout (uses currentColor)' },
    { name: 'logo-wordmark.svg', type: 'SVG', desc: 'Lowercase "savyor" in Newsreader paths (uses currentColor)' },
    { name: 'logo-lockup.svg', type: 'SVG', desc: 'Mark + wordmark, gap = stem width (uses currentColor)' },
    { name: 'favicon.svg', type: 'SVG', desc: 'Deep green (#2F5D50) rounded square with off-white mark' },
    { name: 'apple-touch-icon.png', type: 'PNG (180x180)', desc: 'iOS home screen app icon' },
    { name: 'icon-192.png', type: 'PNG (192x192)', desc: 'Standard PWA app icon' },
    { name: 'icon-512.png', type: 'PNG (512x512)', desc: 'High-res PWA app icon' },
    { name: 'icon-512-maskable.png', type: 'PNG (512x512)', desc: 'Android adaptive maskable icon (80% safe zone)' },
  ];

  const colors = [
    { name: 'Ink', hex: '#1A1A1A', role: 'Primary text, icons on light background' },
    { name: 'Off-white', hex: '#FAFAF8', role: 'App background, icons on dark background' },
    { name: 'Deep Green', hex: '#2F5D50', role: 'App icon background, signature brand accent' },
    { name: 'Secondary', hex: '#6B6B6B', role: 'Muted captions, metadata, tertiary text' },
    { name: 'Border', hex: '#E8E6E1', role: 'Dividers, card borders, subtle containers' },
  ];

  return (
    <div className="min-h-screen bg-[#FAFAF8] text-[#1A1A1A] font-sans">
      {/* Top Bar */}
      <div className="border-b border-[#E8E6E1] bg-[#FAFAF8] sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-3.5 flex items-center justify-between">
          <button
            onClick={onBack}
            className="inline-flex items-center gap-1.5 text-xs text-[#6B6B6B] hover:text-[#1A1A1A] font-normal transition-colors"
          >
            <ArrowLeft size={13} />
            <span>Back to app</span>
          </button>
          <span className="text-xs font-mono text-[#6B6B6B]">Brand Asset Preview</span>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10 space-y-16">
        {/* Header Intro */}
        <div>
          <h1 className="font-serif text-3xl sm:text-4xl font-normal text-[#1A1A1A] tracking-tight">
            Savyor Brand Assets
          </h1>
          <p className="text-sm text-[#6B6B6B] mt-2 font-normal">
            "Save the reel. Keep the idea." — Calm, minimal, literate visual identity.
          </p>
        </div>

        {/* 1. LOCKUP (Light, Dark, Accent) */}
        <section className="space-y-4">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">1. The Lockup</h2>
            <span className="text-xs text-[#6B6B6B]">logo-lockup.svg</span>
          </div>
          <p className="text-xs text-[#6B6B6B] leading-relaxed">
            Mark on the left, lowercase wordmark on the right, vertically centered, with a gap exactly equal to the mark's stem width (5.5 units).
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
            {/* Light Canvas */}
            <div className="border border-[#E8E6E1] bg-[#FAFAF8] rounded-[12px] p-8 flex flex-col items-center justify-center min-h-[160px]">
              <div className="text-[#1A1A1A] w-48 max-w-full">
                <img src="/logo-lockup.svg" alt="Savyor lockup (light)" className="w-full h-auto" />
              </div>
              <span className="text-[11px] font-mono text-[#6B6B6B] mt-6">Ink #1A1A1A on Light</span>
            </div>

            {/* Dark Canvas */}
            <div className="border border-[#1A1A1A] bg-[#1A1A1A] rounded-[12px] p-8 flex flex-col items-center justify-center min-h-[160px]">
              <div className="text-[#FAFAF8] w-48 max-w-full">
                <img
                  src="/logo-lockup.svg"
                  alt="Savyor lockup (dark)"
                  className="w-full h-auto"
                  style={{ filter: 'brightness(0) invert(1)' }}
                />
              </div>
              <span className="text-[11px] font-mono text-[#FAFAF8]/60 mt-6">Off-white #FAFAF8 on Dark</span>
            </div>

            {/* Accent Canvas (Deep Green) */}
            <div className="border border-[#2F5D50] bg-[#2F5D50] rounded-[12px] p-8 flex flex-col items-center justify-center min-h-[160px]">
              <div className="text-[#FAFAF8] w-48 max-w-full">
                <img
                  src="/logo-lockup.svg"
                  alt="Savyor lockup (accent)"
                  className="w-full h-auto"
                  style={{ filter: 'brightness(0) invert(1)' }}
                />
              </div>
              <span className="text-[11px] font-mono text-[#FAFAF8]/70 mt-6">Accent #2F5D50 Background</span>
            </div>
          </div>
        </section>

        {/* 2. THE MARK (At various optical sizes) */}
        <section className="space-y-4">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">2. The Mark (Icon)</h2>
            <span className="text-xs text-[#6B6B6B]">logo-mark.svg</span>
          </div>
          <p className="text-xs text-[#6B6B6B] leading-relaxed">
            Bookmark ribbon with rounded top, bottom V-notch, and a play-triangle cut out of the upper half as negative space. Designed to remain sharp and legible from 16px to 512px.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
            {/* Light Grid */}
            <div className="border border-[#E8E6E1] bg-[#FAFAF8] rounded-[12px] p-6">
              <span className="text-[11px] font-mono text-[#6B6B6B] block mb-6">Light Background (#FAFAF8)</span>
              <div className="flex items-end justify-around gap-4 text-[#1A1A1A]">
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="16px mark" className="w-4 h-4" />
                  <span className="text-[10px] font-mono text-[#6B6B6B]">16px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="24px mark" className="w-6 h-6" />
                  <span className="text-[10px] font-mono text-[#6B6B6B]">24px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="32px mark" className="w-8 h-8" />
                  <span className="text-[10px] font-mono text-[#6B6B6B]">32px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="64px mark" className="w-16 h-16" />
                  <span className="text-[10px] font-mono text-[#6B6B6B]">64px</span>
                </div>
              </div>
            </div>

            {/* Dark Grid */}
            <div className="border border-[#1A1A1A] bg-[#1A1A1A] rounded-[12px] p-6">
              <span className="text-[11px] font-mono text-[#FAFAF8]/60 block mb-6">Dark Background (#1A1A1A)</span>
              <div className="flex items-end justify-around gap-4 text-[#FAFAF8]">
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="16px mark" className="w-4 h-4 invert" />
                  <span className="text-[10px] font-mono text-[#FAFAF8]/50">16px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="24px mark" className="w-6 h-6 invert" />
                  <span className="text-[10px] font-mono text-[#FAFAF8]/50">24px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="32px mark" className="w-8 h-8 invert" />
                  <span className="text-[10px] font-mono text-[#FAFAF8]/50">32px</span>
                </div>
                <div className="flex flex-col items-center gap-2">
                  <img src="/logo-mark.svg" alt="64px mark" className="w-16 h-16 invert" />
                  <span className="text-[10px] font-mono text-[#FAFAF8]/50">64px</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* 3. WORDMARK */}
        <section className="space-y-4">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">3. The Wordmark</h2>
            <span className="text-xs text-[#6B6B6B]">logo-wordmark.svg</span>
          </div>
          <p className="text-xs text-[#6B6B6B] leading-relaxed">
            Lowercase "savyor" converted to vector bezier curves from Newsreader, with slightly tight letter-spacing (-0.02em) for an authoritative, literate feel. Renders without requiring external font files.
          </p>

          <div className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-8 flex flex-col items-center justify-center">
            <img src="/logo-wordmark.svg" alt="Savyor wordmark" className="w-64 max-w-full h-auto py-4" />
            <span className="text-xs text-[#6B6B6B] mt-4 font-serif italic">
              "Save the reel. Keep the idea."
            </span>
          </div>
        </section>

        {/* 4. APP ICONS & FAVICONS */}
        <section className="space-y-4">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">4. App Icons & Favicons</h2>
            <span className="text-xs text-[#6B6B6B]">favicon.svg & PNG formats</span>
          </div>
          <p className="text-xs text-[#6B6B6B] leading-relaxed">
            Clean squircle in signature deep green (#2F5D50) with the off-white mark centered. Includes standard iOS, Android PWA, and maskable formats.
          </p>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-2">
            {/* Favicon */}
            <div className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-5 flex flex-col items-center text-center">
              <img src="/favicon.svg" alt="Favicon" className="w-16 h-16 rounded-[14px]" />
              <span className="text-xs font-medium text-[#1A1A1A] mt-3">favicon.svg</span>
              <span className="text-[10px] text-[#6B6B6B]">Browser tab icon</span>
            </div>

            {/* Apple Touch Icon */}
            <div className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-5 flex flex-col items-center text-center">
              <img src="/apple-touch-icon.png" alt="Apple Touch Icon" className="w-16 h-16 rounded-[14px]" />
              <span className="text-xs font-medium text-[#1A1A1A] mt-3">apple-touch-icon</span>
              <span className="text-[10px] text-[#6B6B6B]">180 × 180 PNG</span>
            </div>

            {/* PWA 192 */}
            <div className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-5 flex flex-col items-center text-center">
              <img src="/icon-192.png" alt="Icon 192" className="w-16 h-16 rounded-[14px]" />
              <span className="text-xs font-medium text-[#1A1A1A] mt-3">icon-192.png</span>
              <span className="text-[10px] text-[#6B6B6B]">192 × 192 PWA</span>
            </div>

            {/* Maskable 512 */}
            <div className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-5 flex flex-col items-center text-center">
              <img src="/icon-512-maskable.png" alt="Maskable 512" className="w-16 h-16" />
              <span className="text-xs font-medium text-[#1A1A1A] mt-3">icon-512-maskable</span>
              <span className="text-[10px] text-[#6B6B6B]">512 × 512 (80% safe)</span>
            </div>
          </div>
        </section>

        {/* 5. COLOR PALETTE */}
        <section className="space-y-4">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">5. Color Palette</h2>
            <span className="text-xs text-[#6B6B6B]">Design tokens</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-5 gap-3 pt-2">
            {colors.map((c) => (
              <div key={c.hex} className="border border-[#E8E6E1] bg-[#FFFFFF] rounded-[12px] p-3 text-left">
                <div
                  className="w-full h-12 rounded-[8px] mb-2.5 border border-[#E8E6E1]"
                  style={{ backgroundColor: c.hex }}
                />
                <span className="text-xs font-medium text-[#1A1A1A] block">{c.name}</span>
                <span className="text-[11px] font-mono text-[#6B6B6B] block mt-0.5">{c.hex}</span>
                <span className="text-[10px] text-[#6B6B6B] block mt-1 leading-tight">{c.role}</span>
              </div>
            ))}
          </div>
        </section>

        {/* 6. DELIVERABLE ASSETS TABLE */}
        <section className="space-y-4 pb-12">
          <div className="border-b border-[#E8E6E1] pb-2 flex items-baseline justify-between">
            <h2 className="font-serif text-xl font-normal text-[#1A1A1A]">6. Deliverable Files</h2>
            <span className="text-xs text-[#6B6B6B]">frontend/public/</span>
          </div>

          <div className="border border-[#E8E6E1] rounded-[12px] overflow-hidden bg-[#FFFFFF] divide-y divide-[#E8E6E1] text-xs">
            {assets.map((a) => (
              <div key={a.name} className="p-3.5 flex items-center justify-between hover:bg-[#FAFAF8] transition-colors">
                <div className="flex items-center gap-3">
                  <span className="font-mono text-[11px] px-2 py-0.5 rounded bg-[#FAFAF8] border border-[#E8E6E1] text-[#1A1A1A]">
                    {a.type}
                  </span>
                  <div>
                    <span className="font-medium text-[#1A1A1A]">{a.name}</span>
                    <span className="text-[#6B6B6B] ml-2 hidden sm:inline">· {a.desc}</span>
                  </div>
                </div>

                <a
                  href={`/${a.name}`}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-[#1A1A1A] hover:underline font-normal text-xs"
                >
                  <span>Open</span>
                  <ExternalLink size={11} />
                </a>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
