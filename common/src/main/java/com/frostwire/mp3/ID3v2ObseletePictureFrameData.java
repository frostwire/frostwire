package com.frostwire.mp3;

public class ID3v2ObseletePictureFrameData extends ID3v2PictureFrameData {
    public ID3v2ObseletePictureFrameData(boolean unsynchronisation) {
        super(unsynchronisation);
    }

    public ID3v2ObseletePictureFrameData(boolean unsynchronisation, String mimeType, byte pictureType, EncodedText description, byte[] imageData) {
        super(unsynchronisation, mimeType, pictureType, description, imageData);
    }

    public ID3v2ObseletePictureFrameData(boolean unsynchronisation, byte[] bytes) throws InvalidDataException {
        super(unsynchronisation, bytes);
    }

    @Override
    protected void unpackFrameData(byte[] bytes) throws InvalidDataException {
        mimeType = "image/" + BufferTools.byteBufferToString(bytes, 1, 3).toLowerCase();
        pictureType = bytes[4];
        byte encoding = EncodedText.checkTextEncoding(bytes[0]);
        int marker = BufferTools.indexOfRequiredTerminator(bytes, 5, encoding);
        description = new EncodedText(encoding, BufferTools.copyBuffer(bytes, 5, marker - 5));
        int imageStart = marker + BufferTools.terminatorLength(encoding);
        imageData = BufferTools.copyBuffer(bytes, imageStart, bytes.length - imageStart);
    }
}
