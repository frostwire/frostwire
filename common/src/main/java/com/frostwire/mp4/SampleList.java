package com.frostwire.mp4;

import java.util.AbstractList;
import java.util.List;

/**
 * Creates a list of <code>ByteBuffer</code>s that represent the samples of a given track.
 */
public class SampleList extends AbstractList<Sample> {
    List<Sample> samples;



    public SampleList(TrackBox trackBox, IsoFile... additionalFragments) {
        Container topLevel = ((Box) trackBox.getParent()).getParent();

        if (trackBox.getParent().getBoxes(MovieExtendsBox.class).isEmpty()) {
            if (additionalFragments.length > 0) {
                throw new RuntimeException("The TrackBox comes from a standard MP4 file. Only use the additionalFragments param if you are dealing with ( fragmented MP4 files AND additional fragments in standalone files )");
            }
            samples = new DefaultMp4SampleList(trackBox.getTrackHeaderBox().getTrackId(), topLevel);
        } else {
            samples = new FragmentedMp4SampleList(trackBox.getTrackHeaderBox().getTrackId(), topLevel, additionalFragments);
        }
    }

    @Override
    public Sample get(int index) {
        return samples.get(index);
    }

    @Override
    public int size() {
        return samples.size();
    }

}
