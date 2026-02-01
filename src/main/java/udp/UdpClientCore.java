package udp;

import core.audio.VoiceFormat;
import core.model.BinaryKind;
import core.net.ChatClientApi;
import core.net.ChatClientListener;
import core.net.LogSink;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI-free UDP client core.
 * - HELLO / LEAVE
 * - user list
 * - text
 * - binary (chunked base64 with BIN_* messages)
 * - voice (buffered send): VOICE_START / VOICE_CHUNK / VOICE_END
 *
 * Packet format: TYPE|FROM|TO|PAYLOAD
 */
public final class UdpClientCore implements ChatClientApi {

    private final InetAddress serverHost;
    private final int serverPort;
    private final String name;
    private final ChatClientListener listener;
    private final LogSink log;

    private DatagramSocket socket;
    private Thread rxThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final UdpReassembler reassembler = new UdpReassembler();
    private UdpChunkTransfer chunker;

    public UdpClientCore(InetAddress serverHost, int serverPort, String name, ChatClientListener listener, LogSink log) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.name = Objects.requireNonNull(name);
        this.listener = Objects.requireNonNull(listener);
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    @Override public void connect() throws IOException {
        if (connected.get()) return;

        socket = new DatagramSocket();
        socket.setSoTimeout(0);
        chunker = new UdpChunkTransfer(socket, serverHost, serverPort);

        connected.set(true);
        rxThread = new Thread(this::rxLoop, "UdpClientRx-" + name);
        rxThread.setDaemon(true);
        rxThread.start();

        sendRaw("HELLO|" + name + "|*|hi");
        log.log("[UDP] Connected as " + name + " to " + serverHost.getHostAddress() + ":" + serverPort);
    }

    @Override public boolean isConnected() { return connected.get(); }

    @Override public String name() { return name; }

    private void rxLoop() {
        byte[] buf = new byte[65_000];
        while (connected.get()) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                String msg = UdpWire.str(pkt.getData(), pkt.getLength());
                String[] parts = msg.split("\\|", 4);
                if (parts.length < 4) continue;

                String type = parts[0];
                String from = parts[1];
                String to = parts[2];
                String payload = parts[3];

                switch (type) {
                    case "CLIENTS" -> {
                        List<String> users = payload.isBlank() ? List.of() : Arrays.asList(payload.split(","));
                        listener.onUserList(users);
                    }
                    case "MSG" -> listener.onText(from, to, payload);

                    // Binary: BIN_START|from|to|id;KIND;filename;totalChunks
                    case "BIN_START" -> reassembler.onBinStart(from, to, payload);
                    case "BIN_CHUNK" -> reassembler.onBinChunk(payload);
                    case "BIN_END" -> {
                        var in = reassembler.onBinEnd(payload);
                        if (in == null) break;
                        if (!in.complete()) {
                            listener.onText("SERVER", name, "Binary transfer missing chunks (UDP loss). Ask sender to resend.");
                            break;
                        }
                        listener.onBinary(in.kind, in.from, in.to, in.name, in.join());
                    }

                    // Voice:
                    // VOICE_START|from|to|id
                    case "VOICE_START" -> listener.onVoiceStart(from, to, VoiceFormat.pcm());

                    // VOICE_CHUNK|from|to|id;seq;base64
                    case "VOICE_CHUNK" -> {
                        String[] p = payload.split(";", 3);
                        if (p.length < 3) break;
                        byte[] chunk = Base64.getDecoder().decode(p[2]);
                        listener.onVoiceChunk(from, to, chunk);
                    }

                    // VOICE_END|from|to|id
                    case "VOICE_END" -> listener.onVoiceEnd(from, to);

                    case "KICK" -> {
                        listener.onText("SERVER", name, "Kicked: " + payload);
                        close();
                    }
                }

            } catch (Exception e) {
                if (connected.get()) log.log("[UDP] RX error: " + e.getMessage());
            }
        }
    }

    @Override public void sendText(String to, String message) throws IOException {
        ensureConnected();
        sendRaw("MSG|" + name + "|" + to + "|" + message.replace("\n", " ").trim());
    }

    @Override public void sendBinary(BinaryKind kind, String to, File file) throws IOException {
        ensureConnected();
        if (file == null || !file.exists()) throw new IOException("File not found");
        byte[] bytes = Files.readAllBytes(file.toPath());
        chunker.sendBinary(kind, name, to, file.getName(), bytes);
    }

    @Override public void sendVoice(String to, AudioFormat format, byte[] pcmBytes) throws IOException {
        ensureConnected();
        if (pcmBytes == null || pcmBytes.length == 0) return;

        String target = (to == null || to.isBlank()) ? "*" : to.trim();

        // For UDP, keep chunks small (Base64 increases size)
        int rawChunkSize = chunker.rawChunkSize; // ~400 bytes
        String id = UUID.randomUUID().toString();

        sendRaw("VOICE_START|" + name + "|" + target + "|" + id);

        Base64.Encoder enc = Base64.getEncoder();
        int seq = 0;
        for (int off = 0; off < pcmBytes.length; off += rawChunkSize) {
            int len = Math.min(rawChunkSize, pcmBytes.length - off);
            byte[] part = Arrays.copyOfRange(pcmBytes, off, off + len);
            String payload = id + ";" + (seq++) + ";" + enc.encodeToString(part);
            sendRaw("VOICE_CHUNK|" + name + "|" + target + "|" + payload);
            // tiny delay reduces burst loss on localhost
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }

        sendRaw("VOICE_END|" + name + "|" + target + "|" + id);
    }

    private void sendRaw(String msg) throws IOException {
        byte[] data = UdpWire.bytes(msg);
        DatagramPacket pkt = new DatagramPacket(data, data.length, serverHost, serverPort);
        socket.send(pkt);
    }

    private void ensureConnected() throws IOException {
        if (!connected.get()) throw new IOException("Not connected");
    }

    @Override public void close() {
        connected.set(false);
        try {
            if (socket != null) {
                try { sendRaw("LEAVE|" + name + "|*|bye"); } catch (Exception ignored) {}
                socket.close();
            }
        } finally {
            socket = null;
        }
    }
}
