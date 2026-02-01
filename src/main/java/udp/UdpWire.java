package udp;

import java.nio.charset.StandardCharsets;

/** Small helpers for UDP payload formatting. */
public final class UdpWire {
    private UdpWire() {}

    public static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static String str(byte[] b, int len) {
        return new String(b, 0, len, StandardCharsets.UTF_8);
    }
}
