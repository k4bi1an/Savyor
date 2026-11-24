# Spike 2C: Native Frame Extraction & LRU Snapshot Cache (No FFmpeg)

## 1. Frame Extraction without FFmpeg
Android's native `MediaMetadataRetriever` provides hardware-accelerated frame extraction directly from MP4 files without bundling a heavy FFmpeg binary:

```kotlin
val retriever = MediaMetadataRetriever()
retriever.setDataSource(videoFile.absolutePath)
// Extract frame at exact microsecond timestamp
val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
```

### Downscaling & WebP Encoding
- **Thumbnail**: Scaled to ~360px width, WebP Lossy (quality 75) -> ~18 KB per reel.
- **Key Moment Snapshots**: Scaled to ~640px width, WebP Lossy (quality 75) -> ~45 KB per snapshot.
- Max 4 snapshots per reel -> ~180 KB snapshots per reel.
- **Zero FFmpeg binary required** for frame extraction, saving ~15 MB from APK size!

---

## 2. LRU Size-Capped Snapshot Cache (100 MB Default)
- Stored under `context.cacheDir/snapshots/`.
- Maintains an in-memory or database index of last access timestamps.
- When total snapshot directory size exceeds 100 MB:
  1. Identifies the oldest un-accessed snapshots.
  2. Ensures the snapshot has already synced to Google Drive.
  3. Evicts local file from the phone cache.
  4. If the user views the note later, the snapshot is transparently re-fetched from Drive on demand.
