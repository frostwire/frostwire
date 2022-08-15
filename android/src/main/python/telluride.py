import youtube_dl
import urllib
import json

def query_video(page_url):
    '''
    query_video: queries metadata for the video in page_url using youtube_dl
    '''
    ydl_opts = {'nocheckcertificate' : True,
                'quiet': True,
                'restrictfilenames': True,
                'format': 'bestaudio/best'
               }
    with youtube_dl.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(urllib.parse.unquote(page_url), download=False)
        return json.dumps(info_dict, indent=4)

if __name__ == '__main__':
    r = query_video("https://www.youtube.com/watch?v=ye2CLllRO8I")
    print(r)
    print(len(r))
