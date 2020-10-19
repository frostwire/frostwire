'''
Telluride Cloud Video Downloader.
Copyright 2020 FrostWire LLC.
Author: @gubatron

A portable and easy to use youtube_dl wrapper by FrostWire.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''
import argparse
import json
import sys
import youtube_dl

BUILD=3

def welcome():
    '''
    Prints the name of the program, build and copyright
    '''
    print()
    print("Telluride Cloud Video Downloader. Build " + str(BUILD))
    print("Copyright 2020 FrostWire LLC. Licensed under Apache 2.0.")
    print()

if __name__ == "__main__":
    welcome()
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--audio-only",
        "-a",
        action='store_true',
        help='Downloads the video and keeps only a separate audio file' +
             ' usually a .mp3. (requires ffmpeg installed in the system)')
    parser.add_argument(
        "--meta-only",
        "-m",
        action='store_true',
        help='Prints a JSON dictionary with all the metadata available on' +
        ' the video file found in the page_url. ' +
        'Does not download the video file')
    parser.add_argument(
        "page_url",
        help="The URL of the page that hosts the video you need to backup locally")
    args, leftovers = parser.parse_known_args()

    audio_only = args.audio_only
    meta_only = args.meta_only
    page_url = args.page_url

    print('Page_URL: <' + args.page_url + '>')
    ydl_opts = {'nocheckcertificate' : True,
                'quiet': False,
                'restrictfilenames': True
                }
    if meta_only:
        ydl_opts['quiet'] = True
        ydl_opts['format'] = 'bestaudio/best'
        with youtube_dl.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(page_url, download=False)
            print(json.dumps(info_dict, indent=2))
            sys.exit(0)

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
