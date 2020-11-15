/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.archiveorg;

/*
{
  "responseHeader":{
    "status":0,
    "QTime":4,
    "params":{
      "json.wrf":"",
      "qin":"superman",
      "fl":"avg_rating,call_number,collection,contributor,coverage,creator,date,description,downloads,foldoutcount,format,headerImage,identifier,imagecount,language,licenseurl,mediatype,month,num_reviews,oai_updatedate,publicdate,publisher,rights,scanningcentre,source,subject,title,type,volume,week,year",
      "sort":"avg_rating desc, downloads desc, createdate desc",
      "indent":"yes",
      "start":"0",
      "q":"(title:superman^100 OR description:superman^15 OR collection:superman^10 OR language:superman^10 OR text:superman^1)",
      "wt":"json",
      "rows":"50"}},
  "response":{"numFound":1972,"start":0,"docs":[
      {
        "title":"Nick Carter, Master Detective",
        "mediatype":"audio",
        "description":"Lon Clark starred as Nick Carter, Master Detective for 12 years (1943-55) over the Mutual Network. This set is vetted to eliminate the various errors in Carter sets on the Internet and is believed to contain all available episodes of Nick Carter.",
        "licenseurl":"http://creativecommons.org/licenses/publicdomain/",
        "publicdate":"2010-09-12T01:53:32Z",
        "downloads":77557,
        "week":905,
        "month":4250,
        "num_reviews":7,
        "avg_rating":5.0,
        "identifier":"nick-carter",
        "subject":[
          "Nick Carter, Old Time Radio"],
        "format":[
          "Archive BitTorrent",
          "Metadata",
          "Ogg Vorbis",
          "VBR MP3"],
        "collection":[
          "oldtimeradio",
          "radioprograms"],
        "oai_updatedate":["2010-09-12T01:53:32Z","2010-09-12T01:52:06Z","2010-09-12T01:57:21Z","2010-09-12T02:19:15Z","2010-09-12T02:44:13Z","2010-09-12T02:52:05Z","2011-02-26T01:45:24Z","2012-12-15T10:54:08Z","2012-12-15T10:53:29Z"]},
      {
 */

/**
 * @author gubatron
 * @author aldenml
 */
public class ArchiveorgResponse {
    public ArchiveorgResponseField response;
}
