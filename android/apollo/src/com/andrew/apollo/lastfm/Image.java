/*
 * Copyright (c) 2012, the Last.fm Java Project and Committers All rights
 * reserved. Redistribution and use of this software in source and binary forms,
 * with or without modification, are permitted provided that the following
 * conditions are met: - Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following disclaimer. -
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. THIS SOFTWARE IS
 * PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.andrew.apollo.lastfm;

import java.util.Locale;

/**
 * An <code>Image</code> contains metadata and URLs for an artist's image.
 * Metadata contains title, votes, format and other. Images are available in
 * various sizes, see {@link ImageSize} for all sizes.
 * 
 * @author Janni Kovacs
 * @see ImageSize
 * @see Artist#getImages(String, String)
 */
public class Image extends ImageHolder {

    final static ItemFactory<Image> FACTORY = new ImageFactory();

    private String url;

    private Image() {
    }

    public String getUrl() {
        return url;
    }

    private static class ImageFactory implements ItemFactory<Image> {
        @Override
        public Image createItemFromElement(final DomElement element) {
            final Image i = new Image();
            i.url = element.getChildText("url");
            loadImages(i, element);
            return i;
        }
    }
}
