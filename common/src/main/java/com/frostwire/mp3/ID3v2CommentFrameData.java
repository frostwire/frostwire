package com.frostwire.mp3;

public class ID3v2CommentFrameData extends AbstractID3v2FrameData {
    private static final String DEFAULT_LANGUAGE = "eng";
    private String language;
    private EncodedText description;
    private EncodedText comment;

    public ID3v2CommentFrameData(boolean unsynchronisation) {
        super(unsynchronisation);
    }

    public ID3v2CommentFrameData(boolean unsynchronisation, String language, EncodedText description, EncodedText comment) {
        super(unsynchronisation);
        this.language = language;
        this.description = description;
        this.comment = comment;
    }

    public ID3v2CommentFrameData(boolean unsynchronisation, byte[] bytes) throws InvalidDataException {
        super(unsynchronisation);
        synchroniseAndUnpackFrameData(bytes);
    }

    protected void unpackFrameData(byte[] bytes) throws InvalidDataException {
        byte encoding = EncodedText.checkTextEncoding(bytes[0]);
        language = BufferTools.byteBufferToString(bytes, 1, 3);
        int marker = BufferTools.indexOfRequiredTerminator(bytes, 4, encoding);
        description = new EncodedText(encoding, BufferTools.copyBuffer(bytes, 4, marker - 4));
        int start = marker + BufferTools.terminatorLength(encoding);
        int end = BufferTools.indexOfTerminator(bytes, start, encoding);
        comment = new EncodedText(encoding, BufferTools.copyBuffer(bytes, start, end - start));
    }

    protected byte[] packFrameData() {
        byte[] bytes = new byte[getLength()];
        if (comment != null) bytes[0] = comment.getTextEncoding();
        else bytes[0] = 0;
        String langPadded;
        if (language == null) {
            langPadded = DEFAULT_LANGUAGE;
        } else if (language.length() > 3) {
            langPadded = language.substring(0, 3);
        } else {
            langPadded = BufferTools.padStringRight(language, 3, ' ');
        }
        BufferTools.stringIntoByteBuffer(langPadded, 0, 3, bytes, 1);
        int descriptionLength = 0;
        if (description != null && description.toBytes().length > 0) {
            descriptionLength = description.toBytes().length;
            BufferTools.copyIntoByteBuffer(description.toBytes(), 0, descriptionLength, bytes, 4);
        }
        // The terminator, one or two zero bytes, is already zero in the new array.
        int terminatorLength = BufferTools.terminatorLength(bytes[0]);
        if (comment != null && comment.toBytes().length > 0) {
            BufferTools.copyIntoByteBuffer(comment.toBytes(), 0, comment.toBytes().length, bytes, descriptionLength + 4 + terminatorLength);
        }
        return bytes;
    }

    protected int getLength() {
        int length = 4 + BufferTools.terminatorLength(comment != null ? comment.getTextEncoding() : 0);
        if (description != null) length += description.toBytes().length;
        if (comment != null) length += comment.toBytes().length;
        return length;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public EncodedText getComment() {
        return comment;
    }

    public void setComment(EncodedText comment) {
        this.comment = comment;
    }

    public EncodedText getDescription() {
        return description;
    }

    public void setDescription(EncodedText description) {
        this.description = description;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ID3v2CommentFrameData)) return false;
        if (!super.equals(obj)) return false;
        ID3v2CommentFrameData other = (ID3v2CommentFrameData) obj;
        if (language == null) {
            if (other.language != null) return false;
        } else if (other.language == null) return false;
        else if (!language.equals(other.language)) return false;
        if (description == null) {
            if (other.description != null) return false;
        } else if (other.description == null) return false;
        else if (!description.equals(other.description)) return false;
        if (comment == null) {
            if (other.comment != null) return false;
        } else if (other.comment == null) return false;
        else if (!comment.equals(other.comment)) return false;
        return true;
    }
}
