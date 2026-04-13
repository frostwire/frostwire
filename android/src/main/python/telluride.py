import sys
import types

# Block _posixsubprocess — it calls fork()/posix_spawn() which crashes
# with SIGSEGV on Android's JVM process. yt_dlp imports subprocess
# transitively, which tries to import _posixsubprocess at the C level.
sys.modules['_posixsubprocess'] = types.ModuleType('_posixsubprocess')

import yt_dlp
import urllib
import json

def yt_dlp_version():
    return yt_dlp.version.__version__

def query_video(page_url):
    '''
    query_video: queries metadata for the video in page_url using yt_dlp
    Returns 'None' on error
    '''
    ydl_opts = {'nocheckcertificate' : True,
                'quiet': True,
                'restrictfilenames': True,
                'format': 'bestaudio/best',
               }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        try:
            info_dict = ydl.extract_info(urllib.parse.unquote(page_url), download=False)
            return json.dumps(info_dict, indent=4)
        except:
            return None

def query_playlist(page_url):
    try:
        ydl_opts = {
            'nocheckcertificate': True,
            'quiet': True,
            'restrictfilenames': True,
            'extract_flat': True,
            'playlist_items': '1-50',
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(urllib.parse.unquote(page_url), download=False)
            playlist_title = info_dict.get('title', '')
            extractor_key = info_dict.get('extractor_key', '') or info_dict.get('extractor', '')
            entries = []
            for entry in (info_dict.get('entries') or []):
                video_id = entry.get('id', '')
                title = entry.get('title', '')
                url = entry.get('url') or entry.get('webpage_url', '')
                if video_id and not url:
                    url = f"https://www.youtube.com/watch?v={video_id}"
                thumbnail = entry.get('thumbnail') or ''
                duration = entry.get('duration')
                upload_date = entry.get('upload_date') or ''
                view_count = entry.get('view_count')
                description = entry.get('description') or ''
                if description:
                    description = description[:200]
                entries.append({
                    'id': video_id,
                    'title': title,
                    'url': url,
                    'thumbnail': thumbnail,
                    'duration': duration,
                    'upload_date': upload_date,
                    'view_count': view_count,
                    'description': description,
                })
            result = {
                'type': 'playlist',
                'title': playlist_title,
                'extractor': extractor_key,
                'entries': entries,
            }
            return json.dumps(result, indent=4)
    except Exception as e:
        return json.dumps({'type': 'error', 'message': str(e)})

if __name__ == '__main__':
    r = query_video("https://www.youtube.com/watch?v=ye2CLllRO8I")
    print(r)

