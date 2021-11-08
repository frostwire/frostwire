# Telluride Cloud Video Downloader.

![Telluride Logo](logo/1024X1024-white-telluride-logo.png)

### A portable and easy to use youtube_dl wrapper by FrostWire.    

## Usage
```
Telluride Cloud Video Downloader. Build 14
Copyright 2020-2021 FrostWire LLC. Licensed under Apache 2.0.

usage: telluride[.exe|_macos|_linux] [-h] [--server] [--port PORT] [--audio-only] [--meta-only]
                    [page_url]

positional arguments:
  page_url              The URL of the page that hosts the video you need to
                        backup locally

optional arguments:
  -h, --help            show this help message and exit
  --server, -s          Launches Telluride as a web server to perform URL
                        queries and return meta data as JSON. There's only one
                        endpoint at the root path. Possible parameters are
                        url=<video_page_url> and shutdown=1 to shutdown the
                        server. The server will only answer to requests from
                        localhost
                        when the request comes from localhost
  --port PORT, -p PORT  HTTP port when running on server mode. Default port
                        number is 47999. This parameter is only taken into
                        account if --server or -s passed
  --audio-only, -a      Downloads the video and keeps only a separate audio
                        file usually a .mp3. (requires ffmpeg installed in the
                        system)
  --meta-only, -m       Prints a JSON dictionary with all the metadata
                        available on the video file found in the page_url.
                        Does not download the video file
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

## Web Server Mode

If you want your app to make repeated requests to telluride it's very expensive starting and destroying a new telluride process every time. By passing the `--server` option and optionally a tcp `--port` number you can run telluride in the background and send requests to the web server from localhost.

The default port number is `47999`, as defined on `server.py`

Telluride will receive your HTTP requests at the root path and it will expect only two possible request parameters:
 - `url=<url encoded video page url>` - It will search for the available metadata and return it in JSON, the same as when you execute telluride in `--meta-only` mode
 - `shutdown=1` - Pass this paremeter if you want to shutdown the telluride web server

**This web server is designed only to receive requests from `localhost`**. If you want to do something like a P2P network of telluride webservers to resolve a lot of these requests, you need to implement all the peer to peer coordination, throttling, caching, anti-DDOS logic in a service or app that launches this server. This is a simple web interface to the local telluride process, a simple lego piece you can use as best you can.

## License
```
Copyright 2020-2021 FrostWire LLC.

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
