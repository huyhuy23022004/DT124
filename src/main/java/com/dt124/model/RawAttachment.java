package com.dt124.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw attachment extracted from a MIME body part, kept only in RAM.
 */
public final class RawAttachment {

    private String fileName = "";
    private String contentType = "";
    private byte[] content = new byte[0];
    private long declaredSize;

    public RawAttachment() {
    }

    public RawAttachment(String fileName, String contentType, byte[] content, long declaredSize) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : content;
        this.declaredSize = declaredSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType == null ? "" : contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content == null ? new byte[0] : content;
    }

    public long getDeclaredSize() {
        return declaredSize;
    }

    public void setDeclaredSize(long declaredSize) {
        this.declaredSize = declaredSize;
    }
}
