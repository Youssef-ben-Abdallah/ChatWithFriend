package tcp;

import core.model.BinaryKind;
import core.net.ChatClientApi;
import core.net.ChatClientListener;
import core.net.LogSink;
import core.util.IOUtil;

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI-free TCP client core.
 * Features:
 * - HELLO handshake
 * - user list updates
 * - text
 * - binary (file/image)
 * - voice message (PCM) sent as: VOICE_START + many VOICE_CHUNK + VOICE_END
 */
public final class TcpClientCore implements ChatClientApi {
    private final String host;
    private final int port;
    private final String name;
    private final ChatClientListener listener;
    private final LogSink log;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread rxThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public TcpClientCore(String host, int port, String name, ChatClientListener listener, LogSink log) {
        this.host = host;
        this.port = port;
        this.name = Objects.requireNonNull(name);
        this.listener = Objects.requireNonNull(listener);
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    @Override public void connect() throws IOException {
        if (connected.get()) return;

        socket = new Socket(host, port);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        TcpWire.sendHeader(out, "HELLO:" + name);

        connected.set(true);
        rxThread = new Thread(this::rxLoop, "TcpClientRx-" + name);
        rxThread.setDaemon(true);
        rxThread.start();
        log.log("[TCP] Connected as " + name + " to " + host + ":" + port);
    }

    @Override public boolean isConnected() { return connected.get(); }

    @Override public String name() { return name; }

    private void rxLoop() {
        try {
            while (connected.get()) {
                String header = in.readUTF();

                if (header.startsWith("KICK:")) {
                    // KICK:SERVER:<to>:<reason>
                    String[] p = header.split(":", 4);
                    String reason = (p.length == 4) ? p[3] : "Kicked by server";
                    listener.onText("SERVER", name, "Kicked: " + reason);
                    close();
                    return;
                }

                if (header.startsWith("USER_LIST:")) {
                    String list = header.substring("USER_LIST:".length());
                    List<String> users = list.isEmpty() ? List.of() : Arrays.asList(list.split(","));
                    listener.onUserList(users);
                    continue;
                }

                if (header.startsWith("TEXT:")) {
                    // TEXT:<from>:<to>:<message>
                    String[] p = header.split(":", 4);
                    if (p.length == 4) listener.onText(p[1], p[2], p[3]);
                    continue;
                }

                if (header.startsWith("BIN:")) {
                    // BIN:<kind>:<from>:<to>:<filename>:<size>
                    String[] p = header.split(":", 6);
                    if (p.length != 6) continue;

                    BinaryKind kind = BinaryKind.valueOf(p[1]);
                    String from = p[2];
                    String to = p[3];
                    String fileName = p[4];
                    long size = Long.parseLong(p[5]);

                    byte[] bytes = TcpWire.readBytes(in, size);
                    listener.onBinary(kind, from, to, fileName, bytes);
                    continue;
                }

                if (header.startsWith("VOICE_START:")) {
                    // VOICE_START:<from>:<to>:<sr>:<ch>:<bits>:<bigEndian>:<signed>
                    String[] p = header.split(":", 8);
                    if (p.length != 8) continue;
                    String from = p[1];
                    String to = p[2];
                    float sr = Float.parseFloat(p[3]);
                    int ch = Integer.parseInt(p[4]);
                    int bits = Integer.parseInt(p[5]);
                    boolean bigEndian = Boolean.parseBoolean(p[6]);
                    boolean signed = Boolean.parseBoolean(p[7]);
                    AudioFormat fmt = new AudioFormat(sr, bits, ch, signed, bigEndian);
                    listener.onVoiceStart(from, to, fmt);
                    continue;
                }

                if (header.startsWith("VOICE_CHUNK:")) {
                    // VOICE_CHUNK:<from>:<to>:<size>
                    String[] p = header.split(":", 4);
                    if (p.length != 4) continue;
                    String from = p[1];
                    String to = p[2];
                    int size = Integer.parseInt(p[3]);
                    byte[] bytes = TcpWire.readBytes(in, size);
                    listener.onVoiceChunk(from, to, bytes);
                    continue;
                }

                if (header.startsWith("VOICE_END:")) {
                    // VOICE_END:<from>:<to>
                    String[] p = header.split(":", 3);
                    if (p.length == 3) listener.onVoiceEnd(p[1], p[2]);
                }
            }
        } catch (Exception e) {
            if (connected.get()) log.log("[TCP] RX error: " + e.getMessage());
        } finally {
            close();
        }
    }

    @Override public void sendText(String to, String message) throws IOException {
        ensureConnected();
        String safe = message.replace("\n", " ").trim();
        TcpWire.sendHeader(out, "TEXT:" + name + ":" + to + ":" + safe);
    }

    @Override public void sendBinary(BinaryKind kind, String to, File file) throws IOException {
        ensureConnected();
        if (file == null || !file.exists()) throw new FileNotFoundException("File not found");
        long size = file.length();
        String fileName = file.getName();
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            TcpWire.sendBytes(out, "BIN:" + kind + ":" + name + ":" + to + ":" + fileName + ":" + size, is, size);
        }
    }

    @Override public void sendVoice(String to, AudioFormat format, byte[] pcmBytes) throws IOException {
        ensureConnected();
        if (pcmBytes == null || pcmBytes.length == 0) return;

        String target = (to == null || to.isBlank()) ? "*" : to.trim();

        // announce
        TcpWire.sendHeader(out, "VOICE_START:" + name + ":" + target + ":" +
                format.getSampleRate() + ":" + format.getChannels() + ":" + format.getSampleSizeInBits() + ":" +
                format.isBigEndian() + ":" + format.getEncoding().toString().toLowerCase().contains("signed"));

        // chunks
        int chunkSize = 1024;
        int off = 0;
        while (off < pcmBytes.length) {
            int len = Math.min(chunkSize, pcmBytes.length - off);
            out.writeUTF("VOICE_CHUNK:" + name + ":" + target + ":" + len);
            out.write(pcmBytes, off, len);
            out.flush();
            off += len;
        }

        // end
        TcpWire.sendHeader(out, "VOICE_END:" + name + ":" + target);
    }

    private void ensureConnected() throws IOException {
        if (!connected.get()) throw new IOException("Not connected");
    }

    @Override public void close() {
        connected.set(false);
        IOUtil.closeQuietly(in);
        IOUtil.closeQuietly(out);
        IOUtil.closeQuietly(socket);
        in = null; out = null; socket = null;
    }
}
