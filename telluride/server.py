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
from sanic import Sanic
from sanic.response import json

def start(build_number, http_port_number, workers_number=4):
  app = Sanic('Telluride Web Server {}'.format(build_number))

  @app.route('/')
  async def root_handler(request):
      return json({'build' : build_number})
  
  app.run(host='127.0.0.1', port=http_port_number, workers=workers_number)
