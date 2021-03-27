'''
Telluride Cloud Video Downloader.
Copyright 2020-2021 FrostWire LLC.
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
# python path imports
import argparse
from datetime import datetime
import json
import sys
import youtube_dl

# our imports
import server

BUILD = 14

def welcome():
    '''
    Prints the name of the program, build and copyright
    '''
    print()
    print("Telluride Cloud Video Downloader. Build " + str(BUILD))
    print("Copyright 2020-{} FrostWire LLC. Licensed under Apache 2.0.".format(datetime.today().year))
    print()

def prepare_parser(parser):
    '''
    Initialize all the possible program options
    '''
    parser.add_argument(
        "--server",
        "-s",
        action="store_true",
        help="Launches Telluride as a web server to perform URL queries and return meta data as JSON. There's only one endpoint at the root path. Possible parameters are url=<video_page_url> and shutdown=1 to shutdown the server. The shutdown parameter will only be considered when the request comes from localhost"
        )
    parser.add_argument(
        "--port",
        "-p",
        default=server.DEFAULT_HTTP_PORT,
        type=int,
        help='HTTP port when running on server mode. Default port number is 47999. This parameter is only taken into account if --server or -s passed'
    )
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
        nargs='?',
        help="The URL of the page that hosts the video you need to backup locally")

if __name__ == "__main__":
    welcome()
    PARSER = argparse.ArgumentParser()
    prepare_parser(PARSER)
    ARGS, LEFTOVERS = PARSER.parse_known_args()

    SERVER_MODE = ARGS.server
    SERVER_PORT = ARGS.port
    AUDIO_ONLY = ARGS.audio_only
    META_ONLY = ARGS.meta_only
    PAGE_URL = ARGS.page_url

    if SERVER_MODE:
        print('Starting Telluride Web Server on port {}'.format(SERVER_PORT))
        server.start(BUILD, SERVER_PORT)
        sys.exit(0)

    print('PAGE_URL: <' + PAGE_URL + '>')
    YDL_OPTS = {'nocheckcertificate' : True,
                'quiet': False,
                'restrictfilenames': True
                }
    if META_ONLY:
        YDL_OPTS['quiet'] = True
        YDL_OPTS['format'] = 'bestaudio/best'
        with youtube_dl.YoutubeDL(YDL_OPTS) as ydl:
            INFO_DICT = ydl.extract_info(PAGE_URL, download=False)
            print(json.dumps(INFO_DICT, indent=2))
            sys.exit(0)

    if AUDIO_ONLY:
        print("Audio-only download.")
        YDL_OPTS['format'] = 'bestaudio/best'
        YDL_OPTS['postprocessors'] = [
            {
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '192',
            }
        ]
    print()

    with youtube_dl.YoutubeDL(YDL_OPTS) as ydl:
        ydl.download([PAGE_URL])
