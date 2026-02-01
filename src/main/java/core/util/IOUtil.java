package core.util;

import java.io.*;
import java.util.Objects;

public final class IOUtil {
    private IOUtil() {}

    public static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }

    /** Read exactly len bytes or throw EOFException. */
    public static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(in);
        int n = 0;
        while (n < len) {
            int r = in.read(buf, off + n, len - n);
            if (r < 0) throw new EOFException("Stream ended early");
            n += r;
        }
    }

    /** Copy exactly total bytes from in to out. */
    public static void copyExactly(InputStream in, OutputStream out, long totalBytes) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long remaining = totalBytes;
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            int r = in.read(buffer, 0, want);
            if (r < 0) throw new EOFException("Stream ended early");
            out.write(buffer, 0, r);
            remaining -= r;
        }
    }
}
