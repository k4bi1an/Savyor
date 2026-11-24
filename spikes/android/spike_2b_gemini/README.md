# Spike 2B: Direct Phone-to-Gemini Multimodal Analysis

## 1. Official Gemini API Specifications (Current Verified Docs)

- **Official API Documentation**: [Google Gemini Files API Reference](https://ai.google.dev/api/files) & [Structured Outputs](https://ai.google.dev/gemini-api/docs/structured-output) (Checked November 2026).
- **Available Models**:
  - `gemini-2.5-flash`: Best multimodal reasoning, audio transcription, on-screen OCR, and low latency.
  - `gemini-1.5-flash`: Fallback.
- **Free Tier Quotas**:
  - 15 RPM (Requests Per Minute)
  - 1,000,000 TPM (Tokens Per Minute)
  - 1,500 RPD (Requests Per Day)
  - Cost: Free of charge when the user supplies their personal Google AI Studio key.
- **Files API Capabilities**:
  - Max file size: 2 GB (Instagram reels are typically 5–25 MB at 480p/720p).
  - Video formats supported: MP4, MOV, WebM, 3GP, AVI.
  - Retention: Up to 48 hours, but Savyor **deletes the file immediately** via `DELETE /v1beta/files/{name}` once inference finishes.
- **Structured Output**: Supported via `response_mime_type: "application/json"` and `response_schema` complying with JSON Schema (OpenAPI 3.0 subset).

---

## 2. Phone Resumable Upload Architecture

Rather than loading large video files into phone RAM (which would trigger out-of-memory errors on lower-tier Android devices), the Android client streams the file in 2 MB chunks using HTTP resumable chunking:

```
[Phone App] ──(1) Start Resumable Upload (Headers & Meta)──> [Google Files API]
            <──(2) Return Upload URL (X-Goog-Upload-URL)─────
[Phone App] ──(3) Stream 2MB Chunks from Disk───────────────> [Google Files API]
            <──(4) Upload Finalized (URI: files/xyz)────────
[Phone App] ──(5) Poll until file state == ACTIVE────────────> [Google Files API]
[Phone App] ──(6) generateContent (JSON Schema)─────────────> [Gemini 2.5 Flash]
            <──(7) Structured JSON Notes returned────────────
[Phone App] ──(8) DELETE /v1beta/files/xyz───────────────────> [Google Files API]
```

---

## 3. Key Storage Security (Android Keystore)

- The user enters their Gemini API key once in the app settings.
- The key is encrypted using **AES-256-GCM** with a hardware-backed master key generated in the **Android Keystore** provider (`AndroidKeyStore`).
- The key is **never printed to logcat**, never written to unencrypted storage, and never included in backups.
- Android auto-backup is explicitly disabled for key storage via `res/xml/data_extraction_rules.xml` (`<exclude domain="sharedpref" path="savyor_secure_keys.xml" />`).
