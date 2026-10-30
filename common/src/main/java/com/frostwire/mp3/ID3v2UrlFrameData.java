package com.frostwire.mp3;

public class ID3v2UrlFrameData extends AbstractID3v2FrameData {
    protected String url;
    protected EncodedText description;

    public ID3v2UrlFrameData(boolean unsynchronisation) {
        super(unsynchronisation);
    }

    public ID3v2UrlFrameData(boolean unsynchronisation, EncodedText description, String url) {
        super(unsynchronisation);
        this.description = description;
        this.url = url;
    }

    public ID3v2UrlFrameData(boolean unsynchronisation, byte[] bytes) throws InvalidDataException {
        super(unsynchronisation);
        synchroniseAndUnpackFrameData(bytes);
    }

    protected void unpackFrameData(byte[] bytes) throws InvalidDataException {
        byte encoding = EncodedText.checkTextEncoding(bytes[0]);
        int marker = BufferTools.indexOfRequiredTerminator(bytes, 1, encoding);
        description = new EncodedText(encoding, BufferTools.copyBuffer(bytes, 1, marker - 1));
        // The URL itself is always ISO-8859-1, ended by a single zero byte or the frame's end.
        marker += BufferTools.terminatorLength(encoding) - 1;
        int length = 0;
        for (int i = marker + 1; i < bytes.length; i++) {
            if (bytes[i] == 0) break;
            length++;
        }
        url = BufferTools.byteBufferToString(bytes, marker + 1, length);
    }

    protected byte[] packFrameData() {
        byte[] bytes = new byte[getLength()];
        if (description != null) bytes[0] = description.getTextEncoding();
        else bytes[0] = 0;
        int descriptionLength = 0;
        if (description != null && description.toBytes().length > 0) {
            descriptionLength = description.toBytes().length;
            BufferTools.copyIntoByteBuffer(description.toBytes(), 0, descriptionLength, bytes, 1);
        }
        // The terminator, one or two zero bytes, is already zero in the new array.
        int terminatorLength = BufferTools.terminatorLength(bytes[0]);
        if (url != null && url.length() > 0) {
            BufferTools.stringIntoByteBuffer(url, 0, url.length(), bytes, descriptionLength + 1 + terminatorLength);
        }
        return bytes;
    }

    protected int getLength() {
        int length = 1 + BufferTools.terminatorLength(description != null ? description.getTextEncoding() : 0);
        if (description != null) length += description.toBytes().length;
        if (url != null) length += url.length();
        return length;
    }

    public EncodedText getDescription() {
        return description;
    }

    public void setDescription(EncodedText description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ID3v2UrlFrameData)) return false;
        if (!super.equals(obj)) return false;
        ID3v2UrlFrameData other = (ID3v2UrlFrameData) obj;
        if (url == null) {
            if (other.url != null) return false;
        } else if (other.url == null) return false;
        else if (!url.equals(other.url)) return false;
        if (description == null) {
            if (other.description != null) return false;
        } else if (other.description == null) return false;
        else if (!description.equals(other.description)) return false;
        return true;
    }
}
