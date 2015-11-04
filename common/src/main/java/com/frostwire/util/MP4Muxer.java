/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.frostwire.mp4.*;
import org.apache.commons.io.IOUtils;


/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class MP4Muxer {

    public void mux(String video, String audio, String output, final MP4Metadata mt) throws IOException {

        FileInputStream videoIn = new FileInputStream(video);
        FileInputStream audioIn = new FileInputStream(audio);

        try {
            FileChannel videoChannel = videoIn.getChannel();
            Movie videoMovie = buildMovie(videoChannel);

            FileChannel audioChannel = audioIn.getChannel();
            Movie audioMovie = buildMovie(audioChannel);

            Movie outMovie = new Movie();

            for (Track trk : videoMovie.getTracks()) {
                outMovie.addTrack(trk);
            }

            for (Track trk : audioMovie.getTracks()) {
                outMovie.addTrack(trk);
            }

            Container out = new DefaultMp4Builder() {
                @Override
                protected FileTypeBox createFileTypeBox(Movie movie) {
                    List<String> minorBrands = new LinkedList<String>();
                    minorBrands.add("iso6");
                    minorBrands.add("avc1");
                    minorBrands.add("mp41");
                    minorBrands.add("\0\0\0\0");

                    return new FileTypeBox("MP4 ", 0, minorBrands);
                }

                @Override
                protected MovieBox createMovieBox(Movie movie, Map<Track, int[]> chunks) {
                    MovieBox moov = super.createMovieBox(movie, chunks);
                    moov.getMovieHeaderBox().setVersion(0);
                    return moov;
                }

                @Override
                protected TrackBox createTrackBox(Track track, Movie movie, Map<Track, int[]> chunks) {
                    TrackBox trak = super.createTrackBox(track, movie, chunks);

                    trak.getTrackHeaderBox().setVersion(0);
                    trak.getTrackHeaderBox().setVolume(1.0f);

                    return trak;
                }

                @Override
                protected Box createUdta(Movie movie) {
                    return mt != null ? addUserDataBox(mt) : null;
                }
            }.build(outMovie);

            FileOutputStream fos = new FileOutputStream(output);
            try {
                out.writeContainer(fos.getChannel());
            } finally {
                IOUtils.closeQuietly(fos);
            }
        } finally {
            IOUtils.closeQuietly(videoIn);
            IOUtils.closeQuietly(audioIn);
        }
    }

    public void demuxAudio(String video, String output, final MP4Metadata mt) throws IOException {

        FileInputStream videoIn = new FileInputStream(video);

        try {
            FileChannel videoChannel = videoIn.getChannel();
            Movie videoMovie = buildMovie(videoChannel);

            Track audioTrack = null;

            for (Track trk : videoMovie.getTracks()) {
                if (trk.getHandler().equals("soun")) {
                    audioTrack = trk;
                    break;
                }
            }

            if (audioTrack == null) {
                IOUtils.closeQuietly(videoIn);
                return;
            }

            Movie outMovie = new Movie();
            outMovie.addTrack(audioTrack);

            Container out = new DefaultMp4Builder() {
                @Override
                protected FileTypeBox createFileTypeBox(Movie movie) {
                    List<String> minorBrands = new LinkedList<String>();
                    minorBrands.add("M4A ");
                    minorBrands.add("mp42");
                    minorBrands.add("isom");
                    minorBrands.add("\0\0\0\0");

                    return new FileTypeBox("M4A ", 0, minorBrands);
                }

                @Override
                protected MovieBox createMovieBox(Movie movie, Map<Track, int[]> chunks) {
                    MovieBox moov = super.createMovieBox(movie, chunks);
                    moov.getMovieHeaderBox().setVersion(0);
                    return moov;
                }

                @Override
                protected TrackBox createTrackBox(Track track, Movie movie, Map<Track, int[]> chunks) {
                    TrackBox trak = super.createTrackBox(track, movie, chunks);

                    trak.getTrackHeaderBox().setVersion(0);
                    trak.getTrackHeaderBox().setVolume(1.0f);

                    return trak;
                }

                @Override
                protected Box createUdta(Movie movie) {
                    return mt != null ? addUserDataBox(mt) : null;
                }
            }.build(outMovie);

            FileOutputStream fos = new FileOutputStream(output);
            try {
                out.writeContainer(fos.getChannel());
            } finally {
                IOUtils.closeQuietly(fos);
            }
        } finally {
            IOUtils.closeQuietly(videoIn);
        }
    }

    private static Movie buildMovie(FileChannel channel) throws IOException {
        BoxParser parser = new PropertyBoxParserImpl() {
            @Override
            public Box parseBox(DataSource byteChannel, Container parent) throws IOException {
                Box box = super.parseBox(byteChannel, parent);

                if (box instanceof AbstractBox) {
                    ((AbstractBox) box).parseDetails();
                }

                return box;
            }
        };
        @SuppressWarnings("resource")
        IsoFile isoFile = new IsoFile(new FileDataSourceImpl(channel), parser);
        Movie m = new Movie();
        List<TrackBox> trackBoxes = isoFile.getMovieBox().getBoxes(TrackBox.class);
        int n = 1;
        for (TrackBox trackBox : trackBoxes) {
            m.addTrack(new Mp4TrackImpl("track"+ n, trackBox));
            n++;
        }

        return m;
    }

    private static UserDataBox addUserDataBox(MP4Metadata mt) {

        //"/moov/udta/meta/ilst/covr/data"
        UserDataBox udta = new UserDataBox();

        MetaBox meta = new MetaBox();
        udta.addBox(meta);

        HandlerBox hdlr = new HandlerBox();
        hdlr.setHandlerType("mdir");
        meta.addBox(hdlr);

        AppleItemListBox ilst = new AppleItemListBox();
        meta.addBox(ilst);

        if (mt.title != null) {
            AppleNameBox cnam = new AppleNameBox();
            cnam.setValue(mt.title);
            ilst.addBox(cnam);
        }

        if (mt.author != null) {
            AppleArtistBox cART = new AppleArtistBox();
            cART.setValue(mt.author);
            ilst.addBox(cART);
        }

        AppleArtist2Box aART = new AppleArtist2Box();
        aART.setValue(mt.title + " " + mt.author);
        ilst.addBox(aART);

        if (mt.source != null) {
            AppleAlbumBox calb = new AppleAlbumBox();
            calb.setValue(mt.title + " " + mt.author + " via " + mt.source);
            ilst.addBox(calb);
        }

        AppleMediaTypeBox stik = new AppleMediaTypeBox();
        stik.setValue(1);
        ilst.addBox(stik);

        if (mt.jpg != null) {
            AppleCoverBox covr = new AppleCoverBox();
            covr.setJpg(mt.jpg);
            ilst.addBox(covr);
        }

        return udta;
    }

    public static final class MP4Metadata {

        public MP4Metadata(String title, String author, String source, byte[] jpg) {
            this.title = title;
            this.author = author;
            this.source = source;
            this.jpg = jpg;
        }

        public final String title;
        public final String author;
        public final String source;
        public final byte[] jpg;
    }

    public static void main(String[] args) throws IOException {
        MP4Metadata d = new MP4Metadata("ti", "au", "sr", null);
        MP4Muxer m = new MP4Muxer();
        m.mux("/Users/aldenml/Downloads/test.m4v", "/Users/aldenml/Downloads/test.m4a", "/Users/aldenml/Downloads/test.mp4", d);
    }
}
