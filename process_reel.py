import os
import re
import time
import json
from typing import Optional, Dict, Any, Callable, List
from pydantic import BaseModel, Field
import yt_dlp
from google import genai
from google.genai import types
from dotenv import load_dotenv

import db
import link_verifier

load_dotenv()

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# ==========================================
# Pydantic Schemas for Structured Note Types
# ==========================================

class RecipeIngredient(BaseModel):
    name: str = Field(description="Name of ingredient")
    quantity: str = Field(default="", description="Quantity and unit if mentioned, or empty string")

class RecipeData(BaseModel):
    servings: Optional[str] = Field(default=None, description="Number of servings if stated, else null")
    prep_or_cook_time: Optional[str] = Field(default=None, description="Time required if stated, else null")
    ingredients: List[RecipeIngredient] = Field(default_factory=list, description="Ingredients with name and quantity")
    steps: List[str] = Field(default_factory=list, description="Step-by-step preparation and cooking instructions")

class WorkoutExercise(BaseModel):
    name: str = Field(description="Exercise name")
    sets: Optional[str] = Field(default=None, description="Number of sets if stated")
    reps_or_duration: Optional[str] = Field(default=None, description="Reps or time duration")
    notes: Optional[str] = Field(default=None, description="Form tips, focus cues, or technique notes")

class WorkoutData(BaseModel):
    goal: Optional[str] = Field(default=None, description="Target muscle groups or workout goal")
    equipment: List[str] = Field(default_factory=list, description="Equipment required or empty if bodyweight")
    exercises: List[WorkoutExercise] = Field(default_factory=list, description="Ordered exercises")
    cautions: List[str] = Field(default_factory=list, description="Safety warnings or caveats")

class TutorialStep(BaseModel):
    step_number: int = Field(description="Step index starting at 1")
    description: str = Field(description="Instruction for this step")
    code_snippet: Optional[str] = Field(default=None, description="Code or CLI command if applicable")
    language: Optional[str] = Field(default=None, description="Language for syntax highlighting if code snippet")

class TutorialData(BaseModel):
    prerequisites: List[str] = Field(default_factory=list, description="Prerequisites or tools needed before starting")
    steps: List[TutorialStep] = Field(default_factory=list, description="Numbered actionable steps")
    result: Optional[str] = Field(default=None, description="Expected outcome or final result")

class ToolOrProductData(BaseModel):
    name: str = Field(description="Tool or product name")
    what_it_does: str = Field(description="Core value proposition or functionality")
    key_features: List[str] = Field(default_factory=list, description="Key features, specs, or capabilities")
    price_if_stated: Optional[str] = Field(default=None, description="Pricing or tier if explicitly stated")
    alternatives_mentioned: List[str] = Field(default_factory=list, description="Alternatives or competitors mentioned")
    link: Optional[str] = Field(default=None, description="Official URL or repo link if stated")

class PlaceData(BaseModel):
    name: str = Field(description="Place, venue, landmark, or attraction name")
    location_as_stated: str = Field(description="City, neighborhood, or country as stated in reel")
    what_to_do_or_eat: List[str] = Field(default_factory=list, description="Highlights, dishes, or activities recommended")
    tips: List[str] = Field(default_factory=list, description="Insider tips, booking advice, or warnings")
    best_time: Optional[str] = Field(default=None, description="Best time or season to visit if stated")

class BookOrMediaData(BaseModel):
    title: str = Field(description="Title of book, movie, podcast, or artwork")
    creator: Optional[str] = Field(default=None, description="Author, director, host, or creator")
    why_recommended: str = Field(description="Core thesis, takeaway, or why recommended")
    where_to_find: Optional[str] = Field(default=None, description="Where to read, watch, or listen")

class AdviceData(BaseModel):
    main_claim: str = Field(description="Core philosophy, mindset lesson, or advice thesis")
    supporting_points: List[str] = Field(default_factory=list, description="Supporting arguments or practical principles")
    caveats: List[str] = Field(default_factory=list, description="Nuances, exceptions, or warnings")

class GeneralData(BaseModel):
    summary: str = Field(description="Comprehensive executive summary")
    key_points: List[str] = Field(default_factory=list, description="Main takeaway points")

class TimestampSegment(BaseModel):
    start: float = Field(description="Start time in seconds")
    end: float = Field(description="End time in seconds")
    text: str = Field(description="Spoken text in this short segment")

class KeyMoment(BaseModel):
    time: float = Field(description="Timestamp in seconds")
    label: str = Field(description="Short concise description of what happens at this moment")

class ExtractedResource(BaseModel):
    name: str = Field(description="Name of tool, repo, product, or concept")
    type: str = Field(description="One of: 'tool', 'github_repo', 'product', 'website', 'concept', 'place'")
    url: str = Field(default="", description="Direct URL visible on screen or in caption. Leave empty string if unknown.")
    description: str = Field(description="Brief explanation of what it is and why it was mentioned")

class StructuredContent(BaseModel):
    type: str = Field(description="One of: 'tutorial', 'code', 'recipe', 'checklist', 'analysis'")
    heading: str = Field(description="Section heading")
    items_or_markdown: str = Field(description="Full formatted markdown content")

class ReelKnowledgeCard(BaseModel):
    title: str = Field(description="Clear, punchy, descriptive title removing clickbait")
    category: str = Field(description="Must match exactly one of the provided category names")
    suggested_new_category: Optional[str] = Field(default=None, description="If none of the allowed categories fits well, suggest a short, high-level new category name. Otherwise null.")
    tags: List[str] = Field(description="3 to 6 concise keyword tags")
    summary: str = Field(description="2-3 sentence executive summary explaining what the reel covers and why it matters")
    transcript: str = Field(description="Complete audio speech transcription. If there is no speech (only music or silence), explicitly state: 'No speech in video (music/silent)'.")
    key_takeaways: List[str] = Field(description="3 to 5 actionable bullet points")
    extracted_resources: List[ExtractedResource] = Field(description="Tools, repositories, products, or concepts mentioned or shown on screen")
    structured_content: StructuredContent = Field(description="Detailed step-by-step breakdown or code block")
    note_type: str = Field(default="general", description="One of: 'recipe', 'workout', 'tutorial', 'tool_or_product', 'place', 'book_or_media', 'advice', 'general'")
    recipe_data: Optional[RecipeData] = Field(default=None)
    workout_data: Optional[WorkoutData] = Field(default=None)
    tutorial_data: Optional[TutorialData] = Field(default=None)
    tool_or_product_data: Optional[ToolOrProductData] = Field(default=None)
    place_data: Optional[PlaceData] = Field(default=None)
    book_or_media_data: Optional[BookOrMediaData] = Field(default=None)
    advice_data: Optional[AdviceData] = Field(default=None)
    general_data: Optional[GeneralData] = Field(default=None)
    key_moments: List[KeyMoment] = Field(default_factory=list, description="3 to 8 key moments in the video with timestamp and label")
    timestamped_transcript: List[TimestampSegment] = Field(default_factory=list, description="Timestamped segments of spoken words")

ALLOWED_NOTE_TYPES = {
    "recipe", "workout", "tutorial", "tool_or_product", "place", "book_or_media", "advice", "general"
}

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
        
        actual_video_path = video_filepath
        if not os.path.exists(actual_video_path):
            for ext in ['mp4', 'mkv', 'webm']:
                cand = os.path.join(videos_dir, f"{shortcode}.{ext}")
                if os.path.exists(cand):
                    actual_video_path = cand
                    break

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
            "duration": info.get("duration", 0),
            "thumbnail": info.get("thumbnail") or "",
            "local_video_path": actual_video_path,
            "local_thumb_path": actual_thumb_path,
            "direct_video_url": info.get("url") or "",
            "webpage_url": info.get("webpage_url") or url,
        }

def normalize_and_validate_ai_output(parsed: ReelKnowledgeCard, max_duration: Optional[float] = None) -> Dict[str, Any]:
    """Validates note_type, clamps/sorts timestamps, extracts type_data, and builds unified dictionary."""
    data = parsed.model_dump()

    # Validate note_type
    n_type = data.get("note_type", "general").lower()
    if n_type not in ALLOWED_NOTE_TYPES:
        n_type = "general"
    data["note_type"] = n_type

    # Extract type_data dictionary
    type_data_map = {
        "recipe": data.get("recipe_data"),
        "workout": data.get("workout_data"),
        "tutorial": data.get("tutorial_data"),
        "tool_or_product": data.get("tool_or_product_data"),
        "place": data.get("place_data"),
        "book_or_media": data.get("book_or_media_data"),
        "advice": data.get("advice_data"),
        "general": data.get("general_data"),
    }
    extracted_td = type_data_map.get(n_type) or {}
    data["type_data"] = extracted_td

    # Validate and sort key_moments
    moments = []
    for km in data.get("key_moments", []):
        t = float(km.get("time", 0))
        if t < 0:
            continue
        if max_duration and max_duration > 0 and t > max_duration:
            t = max_duration
        label = str(km.get("label", "")).strip()
        if label:
            moments.append({"time": round(t, 1), "label": label})
    moments.sort(key=lambda m: m["time"])
    data["key_moments"] = moments

    # Validate and sort timestamped_transcript
    segments = []
    for seg in data.get("timestamped_transcript", []):
        st = float(seg.get("start", 0))
        en = float(seg.get("end", 0))
        if st < 0:
            st = 0
        if en < st:
            en = st + 1.0
        if max_duration and max_duration > 0:
            st = min(st, max_duration)
            en = min(en, max_duration)
        text = str(seg.get("text", "")).strip()
        if text:
            segments.append({"start": round(st, 1), "end": round(en, 1), "text": text})
    segments.sort(key=lambda s: s["start"])
    data["timestamped_transcript"] = segments

    return data

def analyze_with_gemini(media_info: Dict[str, Any], progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    """Uploads video to Gemini Files API, performs structured multimodal extraction, and verifies links."""
    if not GEMINI_API_KEY:
        raise ValueError("GEMINI_API_KEY is not configured in .env.")

    client = genai.Client(api_key=GEMINI_API_KEY)
    video_path = media_info.get("local_video_path")
    if not video_path or not os.path.exists(video_path):
        raise FileNotFoundError(f"Local video file not found at {video_path}")

    if progress_callback:
        progress_callback("Uploading video to Gemini Files API...")

    video_file = client.files.upload(file=video_path)

    attempts = 0
    while video_file.state.name == "PROCESSING" and attempts < 15:
        time.sleep(2)
        video_file = client.files.get(name=video_file.name)
        attempts += 1

    if video_file.state.name == "FAILED":
        raise RuntimeError("Gemini failed to process the uploaded video file.")

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
Analyze the provided video and caption to produce an executive knowledge card.

Original Caption:
{media_info.get('description', '')}

Creator: {media_info.get('uploader')} (@{media_info.get('channel')})
Reel URL: {media_info.get('webpage_url')}
{corrections_block}
MANDATORY RULES:
1. Ground truth rule: Use ONLY information present in the video (audio, visual frames, text overlays, software, code) or caption. Never guess, invent, or extrapolate quantities, prices, URLs, or locations. If a detail is missing, leave it empty or null. If audio is unclear, state so.
2. Transcription: Accurately transcribe spoken words with timestamps in 'timestamped_transcript'. If no speech, set transcript to 'No speech in video (music/silent)'.
3. Key moments: Provide 3 to 8 key moments in the video with timestamp in seconds and a short label.
4. Note type: Classify the video into ONE of: recipe, workout, tutorial, tool_or_product, place, book_or_media, advice, general. Fill in the corresponding data block with structured facts.
5. Category: Select exactly ONE category from [{cat_list_str}]. If none fits well, choose closest and suggest in 'suggested_new_category'.
6. Resources: Extract any tools, repos, products, websites mentioned. If an external URL is visible, include it.
"""

    if progress_callback:
        progress_callback("Gemini is watching video & extracting structured notes...")

    last_err = None
    model_name = "gemini-2.5-flash"

    for attempt in range(2):
        try:
            response = client.models.generate_content(
                model=model_name,
                contents=[video_file, prompt],
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=ReelKnowledgeCard
                )
            )
            parsed_card = ReelKnowledgeCard.model_validate_json(response.text)

            # Cleanup uploaded Gemini file
            try:
                client.files.delete(name=video_file.name)
            except Exception:
                pass

            validated_data = normalize_and_validate_ai_output(parsed_card, max_duration=media_info.get("duration"))
            validated_data["model_used"] = model_name

            # Validate category against active list
            valid_map = {c["name"].lower(): c["name"] for c in categories}
            if validated_data["category"].lower() in valid_map:
                validated_data["category"] = valid_map[validated_data["category"].lower()]
            else:
                validated_data["category"] = "Other"

            # Verify extracted resources links
            if progress_callback:
                progress_callback("Verifying extracted links & resources...")
            validated_data["extracted_resources"] = link_verifier.verify_reel_resources(validated_data.get("extracted_resources", []))

            return validated_data

        except Exception as e:
            last_err = e
            time.sleep(2)

    try:
        client.files.delete(name=video_file.name)
    except Exception:
        pass

    raise RuntimeError(f"Gemini analysis failed after retry: {str(last_err)}")

def process_and_save_reel(url: str, progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    """End-to-end extraction, downloading, Gemini analysis, and database storage with duplicate blocking."""
    shortcode = extract_instagram_shortcode(url)

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
    ai_card_dict = analyze_with_gemini(media_info, progress_callback)

    # 3. Assemble full card
    card_dict = dict(ai_card_dict)
    card_dict["id"] = shortcode
    card_dict["media"] = media_info
    card_dict["source"] = "instagram"

    # 4. Resolve Category ID
    categories = db.get_categories()
    cat_match = next((c for c in categories if c["name"].lower() == card_dict["category"].lower()), None)
    card_dict["category_id"] = cat_match["id"] if cat_match else "other"
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

    ai_card_dict = analyze_with_gemini(media_info, progress_callback)
    if hasattr(ai_card_dict, "model_dump"):
        card_dict = ai_card_dict.model_dump()
    else:
        card_dict = dict(ai_card_dict)
    card_dict["id"] = upload_id
    card_dict["media"] = media_info
    card_dict["source"] = "upload"

    categories = db.get_categories()
    cat_match = next((c for c in categories if c["name"].lower() == card_dict["category"].lower()), None)
    card_dict["category_id"] = cat_match["id"] if cat_match else "other"
    card_dict["status"] = "unread"

    db.save_reel(card_dict)

    if progress_callback:
        progress_callback("Successfully saved to Savyor!")

    return db.get_reel_by_id(upload_id) or card_dict

def reprocess_existing_reel(reel_id: str, progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    """
    Re-analyzes an existing reel using local video file (or re-downloads if missing).
    Preserves all user-edited fields (category_id, category, tags, status, reviewed_at).
    """
    existing = db.get_reel_by_id(reel_id)
    if not existing:
        raise ValueError(f"Reel '{reel_id}' not found.")

    media = existing.get("media", {})
    video_path = media.get("local_video_path")

    # If video file missing, attempt redownload if Instagram URL available
    if not video_path or not os.path.exists(video_path):
        webpage_url = media.get("webpage_url") or f"https://www.instagram.com/reel/{reel_id}/"
        if progress_callback:
            progress_callback("Local video missing, re-downloading...")
        redownloaded = download_reel(webpage_url, reel_id, progress_callback)
        video_path = redownloaded.get("local_video_path")
        media["local_video_path"] = video_path

    if not video_path or not os.path.exists(video_path):
        raise FileNotFoundError(f"Video file for reel '{reel_id}' is unavailable.")

    media_info = {
        "id": reel_id,
        "uploader": media.get("uploader", "Unknown"),
        "channel": media.get("channel", "unknown"),
        "description": media.get("description", existing.get("summary", "")),
        "thumbnail": media.get("thumbnail", ""),
        "local_video_path": video_path,
        "local_thumb_path": media.get("local_thumb_path", ""),
        "direct_video_url": media.get("direct_video_url", ""),
        "webpage_url": media.get("webpage_url", ""),
        "source": existing.get("source", "instagram")
    }

    ai_card_dict = analyze_with_gemini(media_info, progress_callback)
    model_name = ai_card_dict.get("model_used", "gemini-2.5-flash")

    # Update database preserving user edits
    updated = db.reprocess_reel_data(reel_id, ai_card_dict, model_used=model_name)
    if progress_callback:
        progress_callback("Reprocessing complete!")
    return updated or existing
