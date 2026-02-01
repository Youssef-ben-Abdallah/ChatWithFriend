package udp;

import core.model.BinaryKind;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.UUID;

/**
 * Generic chunk sender for UDP binaries.
 * - Sends START x3, CHUNK..., END x3
 * - Uses Base64 for data (demo-friendly)
 */
public final class UdpChunkTransfer {
    private final DatagramSocket socket;
    private final InetAddress serverHost;
    private final int serverPort;

    public int rawChunkSize = 400;

    public UdpChunkTransfer(DatagramSocket socket, InetAddress host, int port) {
        this.socket = socket;
        this.serverHost = host;
        this.serverPort = port;
    }

    public String sendBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes) throws IOException {
        String id = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil(bytes.length / (double) rawChunkSize);

        // START: BIN_START|from|to|id;KIND;filename;totalChunks
        repeatSend(3, "BIN_START|" + from + "|" + to + "|" + id + ";" + kind + ";" + fileName + ";" + totalChunks);

        Base64.Encoder enc = Base64.getEncoder();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * rawChunkSize;
            int end = Math.min(bytes.length, start + rawChunkSize);
            byte[] part = java.util.Arrays.copyOfRange(bytes, start, end);

            String b64 = enc.encodeToString(part);
            sendRaw("BIN_CHUNK|" + from + "|" + to + "|" + id + ";" + i + ";" + b64);
            sleep(2);
        }

        repeatSend(3, "BIN_END|" + from + "|" + to + "|" + id);
        return id;
    }

    public void sendRaw(String msg) throws IOException {
        byte[] bytes = UdpWire.bytes(msg);
        var pkt = new java.net.DatagramPacket(bytes, bytes.length, serverHost, serverPort);
        socket.send(pkt);
    }

    private void repeatSend(int n, String msg) throws IOException {
        for (int k = 0; k < n; k++) { sendRaw(msg); sleep(10); }
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
