import os
import re
import time
import json
from typing import Optional, Dict, Any, Callable
from pydantic import BaseModel, Field
import yt_dlp
from google import genai
from google.genai import types
from dotenv import load_dotenv
import db

load_dotenv()

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Schemas for Gemini Structured Output
class ExtractedResource(BaseModel):
    name: str = Field(description="Name of tool, repo, product, or concept")
    type: str = Field(description="One of: 'tool', 'github_repo', 'product', 'website', 'concept'")
    url: str = Field(description="Direct URL visible on screen or in caption. Leave empty string if unknown.")
    description: str = Field(description="Brief explanation of what it is and why it was mentioned")

class StructuredContent(BaseModel):
    type: str = Field(description="One of: 'tutorial', 'code', 'recipe', 'checklist', 'analysis'")
    heading: str = Field(description="Section heading")
    items_or_markdown: str = Field(description="Full formatted markdown content")

class ReelKnowledgeCard(BaseModel):
    title: str = Field(description="Clear, punchy, descriptive title removing clickbait")
    category: str = Field(description="Must match exactly one of the provided category names")
    suggested_new_category: Optional[str] = Field(default=None, description="If none of the allowed categories fits well, suggest a short, high-level new category name. Otherwise null.")
    tags: list[str] = Field(description="3 to 6 concise keyword tags")
    summary: str = Field(description="2-3 sentence executive summary explaining what the reel covers and why it matters")
    transcript: str = Field(description="Complete audio speech transcription. If there is no speech (only music or silence), explicitly state: 'No speech in video (music/silent)'.")
    key_takeaways: list[str] = Field(description="3 to 5 actionable bullet points")
    extracted_resources: list[ExtractedResource] = Field(description="Tools, repositories, products, or concepts mentioned or shown on screen")
    structured_content: StructuredContent = Field(description="Detailed step-by-step breakdown or code block")

def extract_instagram_shortcode(url: str) -> str:
    """Validates that URL is an Instagram reel/post and extracts normalized shortcode."""
    if not url:
        raise ValueError("URL cannot be empty.")
    pattern = r"instagram\.com/(?:reel|reels|p|share/reel)/([A-Za-z0-9_-]+)(?:[/?#]|$)"
    match = re.search(pattern, url.strip())
    if not match:
        raise ValueError("Invalid Instagram URL. Only Instagram /reel/ or /p/ links are supported.")
    return match.group(1)

def get_normalized_reel_url(shortcode: str) -> str:
    return f"https://www.instagram.com/reel/{shortcode}/"

def download_reel(url: str, shortcode: str, progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    """Downloads video (max 720p progressive MP4) and thumbnail locally using yt-dlp."""
    videos_dir = db.get_videos_dir()
    thumbs_dir = db.get_thumbs_dir()
    os.makedirs(videos_dir, exist_ok=True)
    os.makedirs(thumbs_dir, exist_ok=True)

    video_filepath = os.path.join(videos_dir, f"{shortcode}.mp4")
    thumb_filepath = os.path.join(thumbs_dir, f"{shortcode}.jpg")

    if progress_callback:
        progress_callback("Connecting to Instagram & fetching stream...")

    ydl_opts = {
        'format': 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best',
        'outtmpl': os.path.join(videos_dir, f"{shortcode}.%(ext)s"),
        'writethumbnail': True,
        'quiet': True,
        'no_warnings': True,
        'overwrites': True,
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        
        # Resolve downloaded video path
        actual_video_path = video_filepath
        if not os.path.exists(actual_video_path):
            for ext in ['mp4', 'mkv', 'webm']:
                cand = os.path.join(videos_dir, f"{shortcode}.{ext}")
                if os.path.exists(cand):
                    actual_video_path = cand
                    break

        # Move/standardize thumbnail
        actual_thumb_path = ""
        for ext in ['jpg', 'webp', 'png']:
            cand = os.path.join(videos_dir, f"{shortcode}.{ext}")
            if os.path.exists(cand):
                import shutil
                shutil.move(cand, thumb_filepath)
                actual_thumb_path = thumb_filepath
                break
        if not actual_thumb_path and os.path.exists(thumb_filepath):
            actual_thumb_path = thumb_filepath

        return {
            "id": shortcode,
            "uploader": info.get("uploader") or info.get("channel") or "Instagram Creator",
            "channel": info.get("channel") or info.get("uploader_id") or "creator",
            "description": info.get("description", ""),
            "thumbnail": info.get("thumbnail") or "",
            "local_video_path": actual_video_path,
            "local_thumb_path": actual_thumb_path,
            "direct_video_url": info.get("url") or "",
            "webpage_url": info.get("webpage_url") or url,
        }

def analyze_with_gemini(media_info: Dict[str, Any], progress_callback: Optional[Callable[[str], None]] = None) -> ReelKnowledgeCard:
    """Uploads video to Gemini Files API, analyzes audio, visual frames, caption, and enforces structured category & output."""
    if not GEMINI_API_KEY:
        raise ValueError("GEMINI_API_KEY is not configured in .env.")

    client = genai.Client(api_key=GEMINI_API_KEY)
    video_path = media_info.get("local_video_path")
    if not video_path or not os.path.exists(video_path):
        raise FileNotFoundError(f"Local video file not found at {video_path}")

    if progress_callback:
        progress_callback("Uploading video to Gemini Files API...")

    video_file = client.files.upload(file=video_path)
    
    # Wait for file processing if needed
    attempts = 0
    while video_file.state.name == "PROCESSING" and attempts < 15:
        time.sleep(2)
        video_file = client.files.get(name=video_file.name)
        attempts += 1

    if video_file.state.name == "FAILED":
        raise RuntimeError("Gemini failed to process the uploaded video file.")

    # Fetch active categories and recent user corrections from DB
    categories = db.get_categories()
    category_names = [c["name"] for c in categories]
    cat_list_str = ", ".join(f"'{c}'" for c in category_names)

    recent_corrections = db.get_recent_category_corrections(5)
    corrections_block = ""
    if recent_corrections:
        corrections_block = "\nRecent user category adjustments for your reference:\n" + "\n".join(
            f"- \"{c['title']}\" -> '{c['chosen_category']}'" for c in recent_corrections
        ) + "\n"

    prompt = f"""You are the knowledge extraction engine for Savyor.
Analyze the provided Instagram Reel video and its caption.

Original Caption:
{media_info.get('description', '')}

Creator: {media_info.get('uploader')} (@{media_info.get('channel')})
Reel URL: {media_info.get('webpage_url')}
{corrections_block}
MANDATORY RULES:
1. Use ONLY the video (audio, visual frames, screen text, code) and the caption. NEVER invent, hallucinate, or extrapolate details not present.
2. For the 'transcript' field: Accurately transcribe what is spoken in the audio word-for-word. If there is NO speech (only background music, sound effects, or silence), explicitly state: 'No speech in video (music/silent)'.
3. Read all visible on-screen text, UI overlays, software interfaces, and code snippets.
4. For 'extracted_resources': Include any tools, repositories, products, or websites mentioned in the audio or caption. If a URL is explicitly visible on screen or in the caption, include it in 'url'; otherwise, leave 'url' as an empty string.
5. For 'category': You MUST select exactly ONE category from this allowed list: [{cat_list_str}]. If none fits well, choose the closest existing category as a fallback AND suggest a concise new category in 'suggested_new_category' (e.g. 'Woodworking').
"""

    if progress_callback:
        progress_callback("Gemini is watching video & transcribing audio...")

    # Call with 1 retry
    last_err = None
    for attempt in range(2):
        try:
            response = client.models.generate_content(
                model="gemini-3.5-flash-lite",
                contents=[video_file, prompt],
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=ReelKnowledgeCard
                )
            )
            parsed_data = ReelKnowledgeCard.model_validate_json(response.text)

            # Validate category against active list; fallback to 'Other' if not present
            valid_map = {c["name"].lower(): c["name"] for c in categories}
            if parsed_data.category.lower() in valid_map:
                parsed_data.category = valid_map[parsed_data.category.lower()]
            else:
                parsed_data.category = "Other"
            
            # Clean up uploaded file
            try:
                client.files.delete(name=video_file.name)
            except Exception:
                pass
                
            return parsed_data
        except Exception as e:
            last_err = e
            time.sleep(2)

    # Clean up on failure
    try:
        client.files.delete(name=video_file.name)
    except Exception:
        pass

    raise RuntimeError(f"Gemini analysis failed after retry: {str(last_err)}")

def process_and_save_reel(url: str, progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    """End-to-end extraction, downloading, Gemini analysis, and database storage with duplicate blocking."""
    shortcode = extract_instagram_shortcode(url)
    
    # Duplicate check
    existing = db.get_reel_by_id(shortcode)
    if existing:
        if progress_callback:
            progress_callback("Reel already exists in Savyor.")
        return existing

    normalized_url = get_normalized_reel_url(shortcode)
    
    # 1. Download
    media_info = download_reel(normalized_url, shortcode, progress_callback)
    media_info["source"] = "instagram"
    
    # 2. Analyze with Gemini
    ai_card = analyze_with_gemini(media_info, progress_callback)
    
    # 3. Assemble full card
    card_dict = ai_card.model_dump()
    card_dict["id"] = shortcode
    card_dict["media"] = media_info
    card_dict["source"] = "instagram"

    # 4. Resolve Category ID
    categories = db.get_categories()
    cat_match = next((c for c in categories if c["name"].lower() == ai_card.category.lower()), None)
    card_dict["category_id"] = cat_match["id"] if cat_match else "other"
    card_dict["suggested_new_category"] = ai_card.suggested_new_category
    card_dict["status"] = "unread"
    
    # 5. Save to Database
    db.save_reel(card_dict)
    
    if progress_callback:
        progress_callback("Successfully saved to Savyor!")
        
    return db.get_reel_by_id(shortcode) or card_dict

def process_and_save_upload(
    upload_id: str,
    video_path: str,
    thumb_path: Optional[str] = None,
    original_filename: str = "",
    progress_callback: Optional[Callable[[str], None]] = None
) -> Dict[str, Any]:
    """End-to-end processing for uploaded video: Gemini analysis and database storage."""
    media_info = {
        "id": upload_id,
        "uploader": "Direct Upload",
        "channel": "upload",
        "description": f"Uploaded video: {original_filename}" if original_filename else "Direct video upload",
        "thumbnail": "",
        "local_video_path": video_path,
        "local_thumb_path": thumb_path or "",
        "direct_video_url": "",
        "webpage_url": "",
        "source": "upload"
    }

    ai_card = analyze_with_gemini(media_info, progress_callback)

    card_dict = ai_card.model_dump()
    card_dict["id"] = upload_id
    card_dict["media"] = media_info
    card_dict["source"] = "upload"

    categories = db.get_categories()
    cat_match = next((c for c in categories if c["name"].lower() == ai_card.category.lower()), None)
    card_dict["category_id"] = cat_match["id"] if cat_match else "other"
    card_dict["suggested_new_category"] = ai_card.suggested_new_category
    card_dict["status"] = "unread"

    db.save_reel(card_dict)

    if progress_callback:
        progress_callback("Successfully saved to Savyor!")

    return db.get_reel_by_id(upload_id) or card_dict
