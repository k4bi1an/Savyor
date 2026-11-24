import React, { useState, useRef, useEffect } from 'react';
import { Play, Pause, Volume2, VolumeX, Repeat, AlertCircle, Loader2 } from 'lucide-react';

export default function VideoPlayer({ src, poster, onTimeUpdate, seekTo }) {
  const videoRef = useRef(null);
  const containerRef = useRef(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isLooping, setIsLooping] = useState(true);
  const [isMuted, setIsMuted] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [showControls, setShowControls] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [hasError, setHasError] = useState(false);
  const controlsTimeoutRef = useRef(null);

  // Handle external seek requests
  useEffect(() => {
    if (seekTo && typeof seekTo.time === 'number' && videoRef.current) {
      videoRef.current.currentTime = Math.max(0, seekTo.time);
      videoRef.current.play().catch(() => {});
    }
  }, [seekTo]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    setIsLoading(false);
    setHasError(false);

    const handleTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      if (onTimeUpdate) onTimeUpdate(video.currentTime);
    };
    const handleLoadedMetadata = () => {
      setDuration(video.duration);
      setIsLoading(false);
    };
    const handleCanPlay = () => setIsLoading(false);
    const handleWaiting = () => {
      // Only show buffering spinner if video is playing or trying to play
      if (!video.paused) {
        setIsLoading(true);
      }
    };
    const handlePlaying = () => {
      setIsLoading(false);
      setIsPlaying(true);
    };
    const handlePause = () => {
      setIsLoading(false);
      setIsPlaying(false);
    };
    const handleError = (e) => {
      console.error("Video playback error:", e);
      setIsLoading(false);
      setHasError(true);
    };

    video.addEventListener('timeupdate', handleTimeUpdate);
    video.addEventListener('loadedmetadata', handleLoadedMetadata);
    video.addEventListener('canplay', handleCanPlay);
    video.addEventListener('waiting', handleWaiting);
    video.addEventListener('playing', handlePlaying);
    video.addEventListener('pause', handlePause);
    video.addEventListener('error', handleError);

    return () => {
      video.removeEventListener('timeupdate', handleTimeUpdate);
      video.removeEventListener('loadedmetadata', handleLoadedMetadata);
      video.removeEventListener('canplay', handleCanPlay);
      video.removeEventListener('waiting', handleWaiting);
      video.removeEventListener('playing', handlePlaying);
      video.removeEventListener('pause', handlePause);
      video.removeEventListener('error', handleError);
    };
  }, [src]);

  // Keyboard shortcuts (Space, M, L, Left, Right)
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;
      if (e.code === 'Space') {
        e.preventDefault();
        togglePlay();
      } else if (e.code === 'KeyM') {
        toggleMute();
      } else if (e.code === 'KeyL') {
        toggleLoop();
      } else if (e.code === 'ArrowLeft') {
        if (videoRef.current) videoRef.current.currentTime = Math.max(0, videoRef.current.currentTime - 5);
      } else if (e.code === 'ArrowRight') {
        if (videoRef.current) videoRef.current.currentTime = Math.min(duration, videoRef.current.currentTime + 5);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  });

  const togglePlay = () => {
    if (!videoRef.current) return;
    if (isPlaying) {
      videoRef.current.pause();
    } else {
      videoRef.current.play().catch((err) => {
        console.warn("Autoplay blocked, muting to retry:", err);
        setIsMuted(true);
        videoRef.current.muted = true;
        videoRef.current.play().catch((e) => console.error("Play failed:", e));
      });
    }
  };

  const handleSeek = (e) => {
    const seekTime = parseFloat(e.target.value);
    setCurrentTime(seekTime);
    if (videoRef.current) {
      videoRef.current.currentTime = seekTime;
    }
  };

  const toggleMute = () => {
    if (!videoRef.current) return;
    const nextMute = !isMuted;
    setIsMuted(nextMute);
    videoRef.current.muted = nextMute;
  };

  const toggleSpeed = () => {
    const speeds = [1, 1.25, 1.5, 2];
    const nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.length;
    const nextSpeed = speeds[nextIdx];
    setPlaybackSpeed(nextSpeed);
    if (videoRef.current) videoRef.current.playbackRate = nextSpeed;
  };

  const toggleLoop = () => {
    const nextLoop = !isLooping;
    setIsLooping(nextLoop);
    if (videoRef.current) videoRef.current.loop = nextLoop;
  };

  const formatTime = (secs) => {
    if (!secs || isNaN(secs)) return '0:00';
    const mins = Math.floor(secs / 60);
    const remaining = Math.floor(secs % 60);
    return `${mins}:${remaining < 10 ? '0' : ''}${remaining}`;
  };

  const handleMouseMove = () => {
    setShowControls(true);
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    controlsTimeoutRef.current = setTimeout(() => {
      if (isPlaying) setShowControls(false);
    }, 2500);
  };

  return (
    <div 
      ref={containerRef}
      className="relative rounded-[12px] border border-[#E8E6E1] overflow-hidden bg-black flex items-center justify-center select-none w-full max-w-[340px] mx-auto"
      style={{ aspectRatio: '9/16' }}
      onMouseMove={handleMouseMove}
      onMouseLeave={() => isPlaying && setShowControls(false)}
    >
      {/* Video Element */}
      <video
        ref={videoRef}
        src={src}
        poster={poster}
        loop={isLooping}
        playsInline
        preload="metadata"
        className="w-full h-full object-cover cursor-pointer"
        onClick={togglePlay}
      />

      {/* Buffering Spinner */}
      {isLoading && !hasError && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/30 pointer-events-none">
          <Loader2 size={28} className="text-white animate-spin opacity-80" />
        </div>
      )}

      {/* Error Fallback */}
      {hasError && (
        <div className="absolute inset-0 p-5 flex flex-col items-center justify-center bg-black/85 text-center text-white">
          <AlertCircle size={28} className="text-white/60 mb-2" />
          <p className="text-xs font-normal mb-3 text-white/80">Video unavailable</p>
          <button
            onClick={() => {
              if (videoRef.current) {
                videoRef.current.load();
                videoRef.current.play().catch(console.error);
              }
            }}
            className="px-3 py-1.5 rounded-[8px] bg-white/20 hover:bg-white/30 text-xs text-white"
          >
            Retry
          </button>
        </div>
      )}

      {/* Center play button: SHOW ONLY WHILE PAUSED! */}
      {!isPlaying && !isLoading && !hasError && (
        <button
          onClick={togglePlay}
          className="absolute inset-0 m-auto w-12 h-12 rounded-full bg-[#1A1A1A]/80 text-[#FAFAF8] flex items-center justify-center hover:bg-[#1A1A1A] transition-colors"
          title="Play"
        >
          <Play size={20} className="fill-[#FAFAF8] text-[#FAFAF8] translate-x-0.5" />
        </button>
      )}

      {/* Simplified Bottom Control Bar: play, scrub bar, time, speed, loop */}
      <div 
        className={`absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent p-3 pt-6 transition-opacity duration-200 ${
          showControls ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
      >
        {/* Scrub Bar */}
        <div className="relative w-full mb-2 flex items-center">
          <input
            type="range"
            min="0"
            max={duration || 1}
            step="0.1"
            value={currentTime}
            onChange={handleSeek}
            className="w-full h-1 bg-white/30 rounded appearance-none cursor-pointer accent-white hover:h-1.5 transition-all"
          />
        </div>

        {/* Controls Row */}
        <div className="flex items-center justify-between text-white text-xs">
          <div className="flex items-center gap-2">
            {/* Play / Pause */}
            <button 
              onClick={togglePlay} 
              className="p-1 text-white/90 hover:text-white"
              title={isPlaying ? "Pause (Space)" : "Play (Space)"}
            >
              {isPlaying ? <Pause size={15} /> : <Play size={15} className="fill-white" />}
            </button>

            {/* Time */}
            <span className="font-mono text-[11px] text-white/80">
              {formatTime(currentTime)} / {formatTime(duration)}
            </span>
          </div>

          <div className="flex items-center gap-2">
            {/* Speed */}
            <button
              onClick={toggleSpeed}
              className="px-1.5 py-0.5 rounded text-[11px] font-mono text-white/80 hover:text-white hover:bg-white/10"
              title="Playback Speed"
            >
              {playbackSpeed}x
            </button>

            {/* Mute */}
            <button
              onClick={toggleMute}
              className="p-1 text-white/80 hover:text-white"
              title="Mute/Unmute (M)"
            >
              {isMuted ? <VolumeX size={14} /> : <Volume2 size={14} />}
            </button>

            {/* Loop as small icon */}
            <button
              onClick={toggleLoop}
              className={`p-1 transition-colors ${
                isLooping ? 'text-white' : 'text-white/40 hover:text-white/70'
              }`}
              title="Toggle Loop (L)"
            >
              <Repeat size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
