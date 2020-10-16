import argparse
import youtube_dl

BUILD=1

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio-only", "-a", action='store_true')
    parser.add_argument("page_url")
    args, leftovers = parser.parse_known_args()

    audio_only = args.audio_only
    page_url = args.page_url

    print()
    print("Telluride Cloud Downloader b" + str(BUILD))
    print("Copyright FrostWire LLC 2020")
    print()
    print('Page URL: <' + args.page_url + '>')
    ydl_opts = {'nocheckcertificate' : True,
                'quiet': False,
                'restrictfilenames': True
                }

    if audio_only:
        print("Audio-only download.")
        ydl_opts['format'] = 'bestaudio/best'
        ydl_opts['postprocessors'] = [
            {
               'key': 'FFmpegExtractAudio',
               'preferredcodec': 'mp3',
               'preferredquality': '192',
            }
        ]
    print()

    with youtube_dl.YoutubeDL(ydl_opts) as ydl:
        ydl.download([page_url])

