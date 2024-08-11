'''
Telluride Cloud Video Downloader.
Copyright 2020-2024 FrostWire LLC.
Author: @gubatron

A portable and easy to use yt_dlp wrapper by FrostWire.

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
from datetime import datetime
import argparse
import json
import os
import sys
import yt_dlp

BUILD = 33


def welcome():
    '''
    Prints the name of the program, build and copyright
    '''
    print()
    print("Telluride Cloud Video Downloader. Build " + str(BUILD))
    print(
        f"Copyright 2020-{datetime.today().year} FrostWire LLC. Licensed under Apache 2.0."
    )
    print(f"Python {sys.version}")
    print(sys.version_info)
    print(f"CWD={os.getcwd()}")
    print()


def prepare_options_parser(parser):
    '''
    Initialize all the possible program options
    '''
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


def main():
    '''
    Main function
    '''
    welcome()
    arg_parser = argparse.ArgumentParser()
    prepare_options_parser(arg_parser)
    args, _ = arg_parser.parse_known_args()

    audio_only = args.audio_only
    meta_only = args.meta_only
    page_url = args.page_url

    if page_url is None:
        print('Please pass a video page URL or "--help" for instructions\n')
        sys.exit(1)

    yt_dlp_opts = {
        'nocheckcertificate': True,
        'quiet': False,
        'restrictfilenames': True,
        'trim_file_name': 200
    }
    if meta_only:
        yt_dlp_opts['quiet'] = True
        yt_dlp_opts['format'] = 'bestaudio/best'
        with yt_dlp.YoutubeDL(yt_dlp_opts) as ydl:
            info_dict = ydl.extract_info(page_url, download=False)
            print(json.dumps(info_dict, indent=2))
            sys.exit(0)

    if audio_only:
        print("Audio-only download.")
        yt_dlp_opts['format'] = 'bestaudio/best'
        yt_dlp_opts['postprocessors'] = [{
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '192',
        }]

    with yt_dlp.YoutubeDL(yt_dlp_opts) as ydl:
        ydl.download([page_url])


if __name__ == '__main__':
    main()
