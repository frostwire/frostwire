/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.components.slides;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;

/**
 * Builds {@link Slide} through its constructor instead of letting Gson mutate {@code final} fields
 * reflectively. Reflective final-field mutation warns on current JDKs and will be blocked in a
 * future release.
 */
final class SlideDeserializer implements JsonDeserializer<Slide> {

  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(Slide.class, new SlideDeserializer()).create();

  private SlideDeserializer() {}

  /** Parses a slideshow payload; malformed input throws so the caller falls back. */
  static SlideList parseSlideList(String json) {
    return GSON.fromJson(json, SlideList.class);
  }

  @Override
  public Slide deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    if (json == null || !json.isJsonObject()) {
      throw new JsonParseException("Slide must be a JSON object");
    }
    JsonObject o = json.getAsJsonObject();
    return new Slide(
        str(o, "imageSrc"),
        str(o, "clickURL"),
        num(o, "duration"),
        str(o, "torrent"),
        str(o, "httpDownloadURL"),
        str(o, "language"),
        str(o, "os"),
        str(o, "title"),
        str(o, "author"),
        num(o, "size"),
        (int) num(o, "method"),
        str(o, "md5"),
        str(o, "saveFileAs"),
        str(o, "includedVersions"),
        str(o, "audioURL"),
        str(o, "videoURL"),
        str(o, "facebook"),
        str(o, "twitter"),
        str(o, "youtube"),
        str(o, "instagram"),
        (int) num(o, "flags"));
  }

  private static String str(JsonObject o, String name) {
    JsonElement e = o.get(name);
    if (e == null || e.isJsonNull()) {
      return null;
    }
    try {
      return e.getAsString();
    } catch (UnsupportedOperationException ex) {
      throw new JsonParseException("Slide field '" + name + "' must be a string", ex);
    }
  }

  private static long num(JsonObject o, String name) {
    JsonElement e = o.get(name);
    if (e == null || e.isJsonNull()) {
      return 0L;
    }
    try {
      return e.getAsLong();
    } catch (NumberFormatException | UnsupportedOperationException ex) {
      throw new JsonParseException("Slide field '" + name + "' must be a number", ex);
    }
  }
}
