import os
from dotenv import load_dotenv
from google import genai

load_dotenv()

key = os.getenv("GEMINI_API_KEY")
client = genai.Client(api_key=key)

candidates = [
    "gemini-2.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash-lite",
    "gemini-3.8-flash",
]

for m in candidates:
    print(f"Testing {m}...")
    try:
        resp = client.models.generate_content(
            model=m,
            contents="Say 'Ready.'"
        )
        print(f"-> SUCCESS with {m}:", resp.text.strip())
        break
    except Exception as e:
        print(f"-> Failed {m}:", e)
