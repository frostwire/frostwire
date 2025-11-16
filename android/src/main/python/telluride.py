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

if __name__ == '__main__':
    r = query_video("https://www.youtube.com/watch?v=ye2CLllRO8I")
    print(r)

