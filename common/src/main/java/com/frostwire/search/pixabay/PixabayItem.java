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

package com.frostwire.search.pixabay;

/**
 * @author gubatron
 * @author aldenml
 */
public class PixabayItem {

    // common and image
    public int id;
    public String pageURL;
    public String type;
    public String previewURL;
    public int previewWidth;
    public int previewHeight;
    public String webformatURL;
    public int webformatWidth;
    public int webformatHeight;
    public int imageWidth;
    public int imageHeight;
    public int imageSize;
    public int user_id;
    public String user;
    // video
    public int duration;
    public String picture_id;
    public PixabayVideos videos;
}
