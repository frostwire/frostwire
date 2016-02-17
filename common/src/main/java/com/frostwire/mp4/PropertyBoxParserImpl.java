/*
 * Copyright 2012 Sebastian Annies, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.frostwire.mp4;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Property file based BoxFactory
 */
public class PropertyBoxParserImpl extends AbstractBoxParser {
    Properties mapping;
    Pattern constuctorPattern = Pattern.compile("(.*)\\((.*?)\\)");

    private final boolean parseDetails;

    public PropertyBoxParserImpl(boolean parseDetails, String... customProperties) {
        this.parseDetails = parseDetails;
        mapping = new Properties();
        try {
            loadDefault(mapping);
            for (String customProperty : customProperties) {
                mapping.load(getClass().getResourceAsStream(customProperty));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PropertyBoxParserImpl(String... customProperties) {
        this(true, customProperties);
    }

    public PropertyBoxParserImpl(boolean parseDetails, Properties mapping) {
        this.parseDetails = parseDetails;
        this.mapping = mapping;
    }

    public PropertyBoxParserImpl(Properties mapping) {
        this(true, mapping);
    }

    @Override
    public Box createBox(String type, byte[] userType, String parent) {

        invoke(type, userType, parent);
        String[] param = this.param.get();
        try {
            Class<Box> clazz = (Class<Box>) Class.forName("com.frostwire.mp4." + clazzName.get());
            if (param.length > 0) {
                Class[] constructorArgsClazz = new Class[param.length];
                Object[] constructorArgs = new Object[param.length];
                for (int i = 0; i < param.length; i++) {
                    if ("userType".equals(param[i])) {
                        constructorArgs[i] = userType;
                        constructorArgsClazz[i] = byte[].class;
                    } else if ("type".equals(param[i])) {
                        constructorArgs[i] = type;
                        constructorArgsClazz[i] = String.class;
                    } else if ("parent".equals(param[i])) {
                        constructorArgs[i] = parent;
                        constructorArgsClazz[i] = String.class;
                    } else {
                        throw new InternalError("No such param: " + param[i]);
                    }
                }

                Constructor<Box> constructorObject = clazz.getConstructor(constructorArgsClazz);
                return constructorObject.newInstance(constructorArgs);
            } else {
                return clazz.newInstance();
            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    StringBuilder buildLookupStrings = new StringBuilder();
    ThreadLocal<String> clazzName = new ThreadLocal<String>();
    ThreadLocal<String[]> param = new ThreadLocal<String[]>();
    static String[] EMPTY_STRING_ARRAY = new String[0];

    public void invoke(String type, byte[] userType, String parent) {
        String constructor;
        if (userType != null) {
            if (!"uuid".equals((type))) {
                throw new RuntimeException("we have a userType but no uuid box type. Something's wrong");
            }
            constructor = mapping.getProperty("uuid[" + Hex.encodeHex(userType).toUpperCase() + "]");
            if (constructor == null) {
                constructor = mapping.getProperty((parent) + "-uuid[" + Hex.encodeHex(userType).toUpperCase() + "]");
            }
            if (constructor == null) {
                constructor = mapping.getProperty("uuid");
            }
        } else {
            constructor = mapping.getProperty((type));
            if (constructor == null) {
                String lookup = buildLookupStrings.append(parent).append('-').append(type).toString();
                buildLookupStrings.setLength(0);
                constructor = mapping.getProperty(lookup);

            }
        }
        if (constructor == null) {
            constructor = mapping.getProperty("default");
        }
        if (constructor == null) {
            throw new RuntimeException("No box object found for " + type);
        }
        if (!constructor.endsWith(")")) {
            param.set( EMPTY_STRING_ARRAY);
            clazzName.set(constructor);
        } else {
            Matcher m = constuctorPattern.matcher(constructor);
            boolean matches = m.matches();
            if (!matches) {
                throw new RuntimeException("Cannot work with that constructor: " + constructor);
            }
            clazzName.set( m.group(1));
            if (m.group(2).length() == 0) {
                param.set(EMPTY_STRING_ARRAY);
            } else {
                param.set(m.group(2).length() > 0 ? m.group(2).split(",") : new String[]{});
            }
        }

    }

    @Override
    public Box parseBox(DataSource byteChannel, Container parent) throws IOException {
        Box box = super.parseBox(byteChannel, parent);

        if (parseDetails && box instanceof AbstractBox) {
            AbstractBox abstractBox = (AbstractBox)box;
            if (!abstractBox.isParsed()) {
                //System.err.println(String.format("parsed detail %s", box.getClass().getSimpleName()));
                abstractBox.parseDetails();
            }
        }

        return box;
    }

    private void loadDefault(Properties p) {
        p.put("meta-ilst", "AppleItemListBox");
        p.put("rmra", "AppleReferenceMovieBox");
        p.put("rmda", "AppleReferenceMovieDescriptorBox");
        p.put("rmdr", "AppleDataRateBox");
        p.put("rdrf", "AppleDataReferenceBox");

        p.put("wave", "AppleWaveBox");

        p.put("udta-ccid", "OmaDrmContentIdBox");
        p.put("udta-yrrc", "RecordingYearBox");
        p.put("udta-titl", "TitleBox");
        p.put("udta-dscp", "DescriptionBox");
        p.put("udta-icnu", "odf.OmaDrmIconUriBox");
        p.put("udta-infu", "odf.OmaDrmInfoUrlBox");
        p.put("udta-albm", "AlbumBox");
        p.put("udta-cprt", "CopyrightBox");
        p.put("udta-gnre", "GenreBox");
        p.put("udta-perf", "PerformerBox");
        p.put("udta-auth", "AuthorBox");
        p.put("udta-kywd", "KeywordsBox");
        p.put("udta-loci", "threegpp26244.LocationInformationBox");
        p.put("udta-rtng", "RatingBox");
        p.put("udta-clsf", "ClassificationBox");
        p.put("udta-cdis", "vodafone.ContentDistributorIdBox");
        p.put("udta-albr", "vodafone.AlbumArtistBox");
        p.put("udta-cvru", "OmaDrmCoverUriBox");
        p.put("udta-lrcu", "OmaDrmLyricsUriBox");

        p.put("tx3g", "TextSampleEntry");
        p.put("stsd-text", "QuicktimeTextSampleEntry");
        p.put("enct", "TextSampleEntry(type)");
        p.put("samr", "AudioSampleEntry(type)");
        p.put("sawb", "AudioSampleEntry(type)");
        p.put("mp4a", "AudioSampleEntry(type)");
        p.put("drms", "AudioSampleEntry(type)");
        p.put("stsd-alac", "AudioSampleEntry(type)");
        p.put("mp4s", "MpegSampleEntry(type)");
        p.put("owma", "AudioSampleEntry(type)");
        p.put("ac-3", "AudioSampleEntry(type)");
        p.put("dac3", "AC3SpecificBox");
        p.put("ec-3", "AudioSampleEntry(type)");
        p.put("dec3", "EC3SpecificBox");
        p.put("stsd-lpcm", "AudioSampleEntry(type)");
        p.put("stsd-dtsc", "AudioSampleEntry(type)");
        p.put("stsd-dtsh", "AudioSampleEntry(type)");
        p.put("stsd-dtsl", "AudioSampleEntry(type)");
        p.put("ddts", "DTSSpecificBox");
        p.put("stsd-dtse", "AudioSampleEntry(type)");
        p.put("stsd-mlpa", "AudioSampleEntry(type)");
        p.put("dmlp", "MLPSpecificBox");
        p.put("enca", "AudioSampleEntry(type)");
        p.put("sowt", "AudioSampleEntry(type)");
        p.put("encv", "VisualSampleEntry(type)");
        p.put("apcn", "VisualSampleEntry(type)");
        p.put("mp4v", "VisualSampleEntry(type)");
        p.put("s263", "VisualSampleEntry(type)");
        p.put("avc1", "VisualSampleEntry(type)");
        p.put("avc2", "VisualSampleEntry(type)");
        p.put("dvhe", "VisualSampleEntry(type)");
        p.put("dvav", "VisualSampleEntry(type)");
        p.put("avc3", "VisualSampleEntry(type)");
        p.put("avc4", "VisualSampleEntry(type)");
        p.put("hev1", "VisualSampleEntry(type)");
        p.put("hvc1", "VisualSampleEntry(type)");
        p.put("ovc1", "Ovc1VisualSampleEntryImpl");
        p.put("stpp", "XMLSubtitleSampleEntry");
        p.put("avcC", "AvcConfigurationBox");
        p.put("hvcC", "HevcConfigurationBox");
        p.put("alac", "AppleLosslessSpecificBox");
        p.put("btrt", "BitRateBox");
        p.put("ftyp", "FileTypeBox");
        p.put("mdat", "MediaDataBox");
        p.put("moov", "MovieBox");
        p.put("mvhd", "MovieHeaderBox");
        p.put("trak", "TrackBox");
        p.put("tkhd", "TrackHeaderBox");
        p.put("edts", "EditBox");
        p.put("elst", "EditListBox");
        p.put("mdia", "MediaBox");
        p.put("mdhd", "MediaHeaderBox");
        p.put("hdlr", "HandlerBox");
        p.put("minf", "MediaInformationBox");
        p.put("vmhd", "VideoMediaHeaderBox");
        p.put("smhd", "SoundMediaHeaderBox");
        p.put("sthd", "SubtitleMediaHeaderBox");
        p.put("hmhd", "HintMediaHeaderBox");
        p.put("dinf", "DataInformationBox");
        p.put("dref", "DataReferenceBox");
        p.put("url ", "DataEntryUrlBox");
        p.put("urn ", "DataEntryUrnBox");
        p.put("stbl", "SampleTableBox");
        p.put("ctts", "CompositionTimeToSample");
        p.put("stsd", "SampleDescriptionBox");
        p.put("stts", "TimeToSampleBox");
        p.put("stss", "SyncSampleBox");
        p.put("stsc", "SampleToChunkBox");
        p.put("stsz", "SampleSizeBox");
        p.put("stco", "StaticChunkOffsetBox");
        p.put("subs", "SubSampleInformationBox");
        p.put("udta", "UserDataBox");
        p.put("skip", "FreeSpaceBox");
        p.put("tref", "TrackReferenceBox");
        p.put("iloc", "ItemLocationBox");
        p.put("idat", "ItemDataBox");

        p.put("damr", "AmrSpecificBox");
        p.put("meta", "MetaBox");
        p.put("ipro", "ItemProtectionBox");
        p.put("sinf", "ProtectionSchemeInformationBox");
        p.put("frma", "OriginalFormatBox");
        p.put("schi", "SchemeInformationBox");
        p.put("odkm", "OmaDrmKeyManagenentSystemBox");
        p.put("odaf", "OmaDrmAccessUnitFormatBox");
        p.put("schm", "SchemeTypeBox");
        p.put("uuid", "UserBox(userType)");
        p.put("free", "FreeBox");
        p.put("styp", "SegmentTypeBox");
        p.put("mvex", "MovieExtendsBox");
        p.put("mehd", "MovieExtendsHeaderBox");
        p.put("trex", "TrackExtendsBox");

        p.put("moof", "MovieFragmentBox");
        p.put("mfhd", "MovieFragmentHeaderBox");
        p.put("traf", "TrackFragmentBox");
        p.put("tfhd", "TrackFragmentHeaderBox");
        p.put("trun", "TrackRunBox");
        p.put("sdtp", "SampleDependencyTypeBox");
        p.put("mfra", "MovieFragmentRandomAccessBox");
        p.put("tfra", "TrackFragmentRandomAccessBox");
        p.put("mfro", "MovieFragmentRandomAccessOffsetBox");
        p.put("tfdt", "TrackFragmentBaseMediaDecodeTimeBox");
        p.put("nmhd", "NullMediaHeaderBox");
        p.put("gmhd", "GenericMediaHeaderAtom");
        p.put("gmhd-text", "GenericMediaHeaderTextAtom");
        p.put("gmin", "BaseMediaInfoAtom");
        p.put("cslg", "CompositionShiftLeastGreatestAtom");
        p.put("pdin", "ProgressiveDownloadInformationBox");
        p.put("bloc", "BaseLocationBox");
        p.put("ftab", "threegpp26245.FontTableBox");
        p.put("co64", "ChunkOffset64BitBox");
        p.put("xml ", "XmlBox");
        p.put("avcn", "basemediaformat.AvcNalUnitStorageBox");
        p.put("ainf", "AssetInformationBox");
        p.put("pssh", "ProtectionSystemSpecificHeaderBox");
        p.put("trik", "TrickPlayBox");
        p.put("uuid[A2394F525A9B4F14A2446C427C648DF4]", "PiffSampleEncryptionBox");
        p.put("uuid[8974DBCE7BE74C5184F97148F9882554]", "PiffTrackEncryptionBox");
        p.put("uuid[D4807EF2CA3946958E5426CB9E46A79F]", "TfrfBox");
        p.put("uuid[6D1D9B0542D544E680E2141DAFF757B2]", "TfxdBox");
        p.put("uuid[D08A4F1810F34A82B6C832D8ABA183D3]", "UuidBasedProtectionSystemSpecificHeaderBox");
        p.put("senc", "SampleEncryptionBox");
        p.put("tenc", "TrackEncryptionBox");
        p.put("amf0", "ActionMessageFormat0SampleEntryBox");

        //mapping.put("iods", "ObjectDescriptorBox");
        p.put("esds", "ESDescriptorBox");

        p.put("tmcd", "TimeCodeBox");
        p.put("sidx", "SegmentIndexBox");

        p.put("sbgp", "SampleToGroupBox");
        p.put("sgpd", "SampleGroupDescriptionBox");

        p.put("tapt", "TrackApertureModeDimensionAtom");
        p.put("clef", "CleanApertureAtom");
        p.put("prof", "TrackProductionApertureDimensionsAtom");
        p.put("enof", "TrackEncodedPixelsDimensionsAtom");
        p.put("pasp", "PixelAspectRationAtom");
        p.put("load", "TrackLoadSettingsAtom");

        p.put("default", "UnknownBox(type)");

        p.put("\u00A9nam", "AppleNameBox");
        p.put("\u00A9ART", "AppleArtistBox");
        p.put("aART", "AppleArtist2Box");
        p.put("\u00A9alb", "AppleAlbumBox");
        p.put("\u00A9gen", "AppleGenreBox");
        p.put("gnre", "AppleGenreIDBox");
        //mapping.put("\u00A9day", "AppleRecordingYearBox");
        p.put("\u00A9day", "AppleRecordingYear2Box");
        p.put("trkn", "AppleTrackNumberBox");
        p.put("cpil", "AppleCompilationBox");
        p.put("pgap", "AppleGaplessPlaybackBox");
        p.put("disk", "AppleDiskNumberBox");
        p.put("apID", "AppleAppleIdBox");
        p.put("cprt", "AppleCopyrightBox");
        p.put("atID", "Apple_atIDBox");
        p.put("geID", "Apple_geIDBox");
        p.put("sfID", "AppleCountryTypeBoxBox");
        p.put("desc", "AppleDescriptionBox");
        p.put("tvnn", "AppleTVNetworkBox");
        p.put("tvsh", "AppleTVShowBox");
        p.put("tven", "AppleTVEpisodeNumberBox");
        p.put("tvsn", "AppleTVSeasonBox");
        p.put("tves", "AppleTVEpisodeBox");
        p.put("xid ", "Apple_xid_Box");
        p.put("flvr", "Apple_flvr_Box");
        p.put("sdes", "AppleShortDescriptionBox");
        p.put("ldes", "AppleLongDescriptionBox");
        p.put("soal", "AppleSortAlbumBox");
        p.put("purd", "ApplePurchaseDateBox");
        p.put("stik", "AppleMediaTypeBox");

        // added by Tobias Bley / UltraMixer (04/25/2014)
        p.put("\u00A9cmt", "AppleCommentBox");
        p.put("tmpo", "AppleTempoBox");
        p.put("\u00A9too", "AppleEncoderBox");
        p.put("\u00A9wrt", "AppleTrackAuthorBox");
        p.put("\u00A9grp", "AppleGroupingBox");
        p.put("covr", "AppleCoverBox");
        p.put("\u00A9lyr", "AppleLyricsBox");
        p.put("cinf", "ContentInformationBox");
        p.put("tibr", "com.mp4parser.iso14496.part15.TierBitRateBox");
        p.put("tiri", "com.mp4parser.iso14496.part15.TierInfoBox");
        p.put("svpr", "com.mp4parser.iso14496.part15.PriotityRangeBox");
        p.put("emsg", "com.mp4parser.iso23009.part1.EventMessageBox");
        p.put("saio", "SampleAuxiliaryInformationOffsetsBox");
        p.put("saiz", "SampleAuxiliaryInformationSizesBox");
        p.put("vttC", "com.mp4parser.iso14496.part30.WebVTTConfigurationBox");
        p.put("vlab", "com.mp4parser.iso14496.part30.WebVTTSourceLabelBox");
        p.put("wvtt", "com.mp4parser.iso14496.part30.WebVTTSampleEntry");

        // added by marwatk (2/24/2014)
        p.put("Xtra", "XtraBox");
        p.put("\u00A9xyz", "AppleGPSCoordinatesBox");

        p.put("hint", "TrackReferenceTypeBox(type)");
        p.put("cdsc", "TrackReferenceTypeBox(type)");
        p.put("hind", "TrackReferenceTypeBox(type)");
        p.put("vdep", "TrackReferenceTypeBox(type)");
        p.put("vplx", "TrackReferenceTypeBox(type)");

        p.put("rtp ", "HintSampleEntry(type)");
        p.put("srtp", "HintSampleEntry(type)");
    }
}
