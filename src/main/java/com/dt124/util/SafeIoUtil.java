package com.dt124.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Safe I/O helpers. Attachment reading is capped to prevent DoS / zip-bomb style resource exhaustion
 * (see the safety requirements: never write attachments to disk as executables and never open them).
 */
public final class SafeIoUtil {

    /** Maximum number of bytes of an attachment we are willing to read into RAM (5 MB). */
    public static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    private SafeIoUtil() {
    }

    /**
     * Reads at most {@code maxBytes} from the stream. Returns the bytes read (the stream is fully
     * drained but no more than {@code maxBytes} are retained).
     */
    public static byte[] readUpTo(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            if (total < maxBytes) {
                int remaining = maxBytes - total;
                int toWrite = Math.min(remaining, read);
                buffer.write(chunk, 0, toWrite);
                total += toWrite;
            }
        }
        return buffer.toByteArray();
    }

    public static byte[] readUpTo(InputStream in) throws IOException {
        return readUpTo(in, MAX_ATTACHMENT_BYTES);
    }
}
