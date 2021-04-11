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
import sys
import urllib
import youtube_dl
from sanic import Sanic
from sanic.response import json

DEFAULT_HTTP_PORT = 47999

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
        return json(info_dict)

def start(build_number, http_port_number=DEFAULT_HTTP_PORT, workers_number=1):
    '''
    starts the web server.
    Parameters:
    build_number: telluride build number
    http_port_number
    workers_number
    '''
    app = Sanic('Telluride Web Server {}'.format(build_number))

    @app.route('/')
    async def root_handler(request):
        '''
        http handler, rejects connections not coming from localhost|127.0.0.1
        url parameters:
        url=<url encoded video page url to obtain json metadata from>
        [shutdown=1] if passed it will shutdown the server
        '''
        #pylint: disable=unused-variable
        if request.ip != '127.0.0.1' and request.ip != 'localhost':
            return json({'build': build_number, 'message': 'gtfo'})
        query = dict(request.query_args)
        if 'shutdown' in query and (query['shutdown'] == '1' or query['shutdown'].lower() == 'true'):
            sys.exit(0)
            return
        if 'url' in query:
            return query_video(query['url'])
        return json({'build' : build_number, 'message': 'no valid parameters received'})

    app.run(host='127.0.0.1', port=http_port_number, workers=workers_number)
