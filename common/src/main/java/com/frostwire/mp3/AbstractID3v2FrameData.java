package com.frostwire.mp3;

public abstract class AbstractID3v2FrameData {
    boolean unsynchronisation;

    public AbstractID3v2FrameData(boolean unsynchronisation) {
        this.unsynchronisation = unsynchronisation;
    }

    protected void synchroniseAndUnpackFrameData(byte[] bytes) throws InvalidDataException {
        try {
            if (unsynchronisation && BufferTools.sizeSynchronisationWouldSubtract(bytes) > 0) {
                byte[] synchronisedBytes = BufferTools.synchroniseBuffer(bytes);
                unpackFrameData(synchronisedBytes);
            } else {
                unpackFrameData(bytes);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // The frame's fields (encoding byte, MIME type, picture type, terminators) come from the
            // file and ran past the end of its data, like a truncated tag in unpackTag.
            throw new InvalidDataException("Frame data is shorter than its fields", e);
        }
    }

    protected byte[] packAndUnsynchroniseFrameData() {
        byte[] bytes = packFrameData();
        if (unsynchronisation && BufferTools.sizeUnsynchronisationWouldAdd(bytes) > 0) {
            return BufferTools.unsynchroniseBuffer(bytes);
        }
        return bytes;
    }

    protected byte[] toBytes() {
        return packAndUnsynchroniseFrameData();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractID3v2FrameData)) return false;
        AbstractID3v2FrameData other = (AbstractID3v2FrameData) obj;
        return unsynchronisation == other.unsynchronisation;
    }

    protected abstract void unpackFrameData(byte[] bytes) throws InvalidDataException;

    protected abstract byte[] packFrameData();

    protected abstract int getLength();
}
