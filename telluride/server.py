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
from sanic import Sanic
from sanic.response import json

def query_video(page_url):
  YDL_OPTS = {'nocheckcertificate' : True,
              'quiet': True,
              'restrictfilenames': True,
              'format': 'bestaudio/best'
              }
  with youtube_dl.YoutubeDL(YDL_OPTS) as ydl:
    INFO_DICT = ydl.extract_info(page_url, download=False)
    return json(INFO_DICT)


def start(build_number, http_port_number, workers_number=2):
  app = Sanic('Telluride Web Server {}'.format(build_number))

  @app.route('/')
  async def root_handler(request):
    if request.ip != '127.0.0.1' and request.ip != 'localhost':
      return json({'build': build_number, 'message': 'gtfo'})
    query = dict(request.query_args)
    if 'shutdown' in query and (query['shutdown'] == '1' or query['shutdown'].lower() == 'true'):
      return json({'build' : build_number, 'message': 'Shutting down'})


    return json({'build' : build_number})

  app.run(host='127.0.0.1', port=http_port_number, workers=workers_number)
