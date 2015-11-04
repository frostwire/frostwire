package com.frostwire.mp4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public interface Sample {

    void writeTo(WritableByteChannel channel) throws IOException;

    long getSize();

    ByteBuffer asByteBuffer();

}
