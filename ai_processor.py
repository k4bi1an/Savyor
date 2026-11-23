import os
import json
from google import genai
from google.genai import types

EXTRACTION_SYSTEM_PROMPT = """
You are the AI brain of an elite visual knowledge library that combines Pinterest (aesthetic cards) and Notion (deep structured notes) for Instagram Reels.

Your job is to analyze the extracted metadata, caption, and transcript of a reel and produce a high-fidelity, categorized knowledge card.

You must return a valid JSON object matching this schema:
{
  "title": "A punchy, clean title (remove clickbait, state exactly what this is)",
  "category": "One of: Tech & Code, Health & Fitness, Business & Finance, Productivity & Tools, Cooking & Recipes, Creative & Design, Life & Mindset, Other",
  "tags": ["tag1", "tag2", "tag3"],
  "summary": "A 2-3 sentence executive summary of why this reel is valuable",
  "key_takeaways": [
    "Clear, actionable bullet point 1",
    "Clear, actionable bullet point 2"
  ],
  "extracted_resources": [
    {
      "name": "Name of tool, repo, product, or concept",
      "type": "github_repo | tool | product | website | book | concept",
      "url": "Direct URL or search query URL if known (e.g. https://github.com/...)",
      "description": "Brief description of what it is"
    }
  ],
  "structured_content": {
    "type": "code | recipe | checklist | tutorial | analysis",
    "content": "Formatted markdown block: e.g. code snippets with syntax highlighting, step-by-step numbered instructions, or ingredient checklist"
  }
}
"""

def analyze_reel_content(metadata: dict, api_key: str = None) -> dict:
    key = api_key or os.getenv("GEMINI_API_KEY")
    if not key:
        return {"error": "GEMINI_API_KEY is required to run the AI processing."}
    
    client = genai.Client(api_key=key)
    
    prompt = f"""
Analyze this reel:
Creator: {metadata.get('uploader')} (@{metadata.get('channel')})
Original Caption:
{metadata.get('description')}

Duration: {metadata.get('duration')}s
URL: {metadata.get('webpage_url')}

Generate the structured knowledge card according to the system instructions.
"""
    
    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=EXTRACTION_SYSTEM_PROMPT,
            response_mime_type="application/json"
        )
    )
    
    return json.loads(response.text)

if __name__ == "__main__":
    # Test with dummy key notice
    print("AI Processor module loaded successfully.")
