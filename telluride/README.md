# Telluride Cloud Video Downloader.

![Telluride Logo](logo/1024X1024-white-telluride-logo.png)

### A portable and easy to use youtube_dl wrapper by FrostWire.    

## Usage
```
usage: telluride[.exe|_macos|_linux] [-h] [--audio-only] [--meta-only] page_url

positional arguments:
  page_url          The URL of the page that hosts the video you need to
                    backup locally

optional arguments:
  -h, --help        show this help message and exit
  --audio-only, -a  Downloads the video and keeps only a separate audio file
                    usually a .mp3. (requires ffmpeg installed in the system)
  --meta-only, -m   Prints a JSON dictionary with all the metadata available
                    on the video file found in the page_url. Does not download
                    the video file
```

## Building

```bash
$ ./configure.sh # tries to install all dependencies
$ ./build.sh     # runs pylint on the code, pauses, performs binary packaging
```

You should end up with a stand-alone executable for the platform you are on:

 - telluride.exe (Windows)
 - telluride_macos (macOS)
 - telluride_linux (Linux)

## Requirements
 Aside from `bash`,`python3` and `ffmpeg`, the rest should be installed by the `configure.sh` script.
    
 - `bash`
 - `python3`, `pip`, `pylint`
 - `ffmpeg` (for audio-only downloads. audio is extracted from the downloaded video)
 - `youtube_dl`, `pycryptodome`, `pyinstaller`

## License
```
Copyright 2020 FrostWire LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    
    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Donations

If you find this software useful, please support or donate to this project what you can at [https://www.frostwire.com/give](https://www.frostwire.com/give)
