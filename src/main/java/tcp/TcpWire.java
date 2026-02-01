package tcp;

import core.util.IOUtil;

import java.io.*;

/**
 * TCP framing:
 * - Header: DataOutputStream.writeUTF(String)
 * - Optional bytes: for frames that include a ':<size>' suffix
 */
public final class TcpWire {
    private TcpWire() {}

    public static void sendHeader(DataOutputStream out, String header) throws IOException {
        out.writeUTF(header);
        out.flush();
    }

    public static void sendBytes(DataOutputStream out, String headerWithSizeSuffix, InputStream bytes, long size) throws IOException {
        // headerWithSizeSuffix should already contain the size (for debugging / readability)
        out.writeUTF(headerWithSizeSuffix);
        IOUtil.copyExactly(bytes, out, size);
        out.flush();
    }

    public static byte[] readBytes(DataInputStream in, long size) throws IOException {
        if (size > Integer.MAX_VALUE) throw new IOException("Payload too large for demo: " + size);
        byte[] data = new byte[(int) size];
        IOUtil.readFully(in, data, 0, data.length);
        return data;
    }
}
