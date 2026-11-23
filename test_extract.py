import json
import yt_dlp

def extract_reel_metadata(url: str):
    ydl_opts = {
        'skip_download': True,
        'extract_flat': False,
        'quiet': True,
        'no_warnings': True,
    }
    
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        try:
            info = ydl.extract_info(url, download=False)
            metadata = {
                'id': info.get('id'),
                'title': info.get('title'),
                'description': info.get('description'),
                'uploader': info.get('uploader'),
                'uploader_id': info.get('uploader_id'),
                'channel': info.get('channel'),
                'duration': info.get('duration'),
                'thumbnail': info.get('thumbnail'),
                'webpage_url': info.get('webpage_url'),
                'direct_video_url': info.get('url'),
                'timestamp': info.get('timestamp'),
            }
            return metadata
        except Exception as e:
            return {'error': str(e)}

if __name__ == '__main__':
    urls = [
        "https://www.instagram.com/reel/Df-OIaqgGZy/",
        "https://www.instagram.com/reel/DfBEEFBo52M/"
    ]
    for idx, u in enumerate(urls, 1):
        print(f"=== Testing Reel {idx}: {u} ===")
        res = extract_reel_metadata(u)
        print(json.dumps(res, indent=2))
