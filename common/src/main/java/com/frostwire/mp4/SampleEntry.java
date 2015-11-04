package com.frostwire.mp4;

/**
 * Created by sannies on 30.05.13.
 */
public interface SampleEntry extends Box, Container {
    int getDataReferenceIndex();
    void setDataReferenceIndex(int dataReferenceIndex);
}
