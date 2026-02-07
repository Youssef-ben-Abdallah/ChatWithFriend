package udp;

import core.audio.VoiceFormat;
import core.net.ChatClientListener;
import core.net.LogSink;
import core.net.ServerControlApi;
import core.net.ServerControlListener;

import java.net.*;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI-free UDP server.
 * Packet format: TYPE|FROM|TO|PAYLOAD
 *
 * Supported:
 * - HELLO (register)
 * - LEAVE (unregister)
 * - CLIENTS (server broadcast)
 * - MSG (text)
 * - BIN_START / BIN_CHUNK / BIN_END
 * - VOICE_START / VOICE_CHUNK / VOICE_END
 * - KICK (server -> client)
 */
public final class UdpServerCore implements ServerControlApi {
    private final int port;
    private final LogSink log;

    private volatile ServerControlListener listener;
    private volatile ChatClientListener chatListener;

    private DatagramSocket socket;
    private Thread rxThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<String, SocketAddress> clients = new ConcurrentHashMap<>();
    private final UdpReassembler reassembler = new UdpReassembler();

    public UdpServerCore(int port, LogSink log) {
        this.port = port;
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    @Override public void setListener(ServerControlListener listener) {
        this.listener = listener;
    }

    public void setChatListener(ChatClientListener chatListener) {
        this.chatListener = chatListener;
    }

    @Override public void start() throws Exception {
        if (running.get()) return;
        socket = new DatagramSocket(port);
        running.set(true);

        rxThread = new Thread(this::loop, "UdpServerRx");
        rxThread.setDaemon(true);
        rxThread.start();
        log.log("[UDP] Server listening on " + port);
    }

    @Override public boolean isRunning() { return running.get(); }

    @Override public List<String> getClients() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        return names;
    }

    @Override public void kick(String name, String reason) {
        if (name == null || name.isBlank()) return;

        SocketAddress addr = clients.remove(name);
        if (addr == null) return;

        String r = (reason == null || reason.isBlank()) ? "Removed by server" : reason.trim();
        try {
            send(addr, "KICK|SERVER|" + name + "|" + r);
        } catch (Exception ignored) {}

        log.log("[UDP] Kicked " + name + " (" + r + ")");
        try { broadcastClients(); } catch (Exception ignored) {}
    }

    private void loop() {
        byte[] buf = new byte[65_000];
        while (running.get()) {
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
                    case "HELLO" -> {
                        clients.put(from, pkt.getSocketAddress());
                        log.log("[UDP] " + from + " joined");
                        broadcastClients();
                    }
                    case "LEAVE" -> {
                        clients.remove(from);
                        log.log("[UDP] " + from + " left");
                        broadcastClients();
                    }
                    case "MSG", "BIN_START", "BIN_CHUNK", "BIN_END", "VOICE_START", "VOICE_CHUNK", "VOICE_END" -> {
                        forward(msg, from, to);
                        notifyChat(type, from, to, payload);
                    }
                    default -> { /* ignore */ }
                }

            } catch (Exception e) {
                if (running.get()) log.log("[UDP] RX error: " + e.getMessage());
            }
        }
    }

    private void forward(String raw, String from, String to) throws Exception {
        if ("*".equals(to)) {
            for (SocketAddress addr : clients.values()) send(addr, raw);
        } else {
            SocketAddress addr = clients.get(to);
            if (addr == null) {
                sendTo(from, "MSG|SERVER|" + from + "|User '" + to + "' not online.");
                return;
            }
            send(addr, raw);

            // echo to sender so they see their private transfers too
            SocketAddress back = clients.get(from);
            if (back != null) send(back, raw);
        }
    }

    private void broadcastClients() throws Exception {
        List<String> names = getClients();
        String raw = "CLIENTS|SERVER|*|" + String.join(",", names);
        for (SocketAddress addr : clients.values()) send(addr, raw);

        ServerControlListener l = listener;
        if (l != null) {
            try { l.onClientsChanged(names); } catch (Exception ignored) {}
        }
    }

    private void notifyChat(String type, String from, String to, String payload) {
        ChatClientListener l = chatListener;
        if (l == null) return;

        try {
            switch (type) {
                case "MSG" -> l.onText(from, to, payload);
                case "BIN_START" -> reassembler.onBinStart(from, to, payload);
                case "BIN_CHUNK" -> reassembler.onBinChunk(payload);
                case "BIN_END" -> {
                    UdpReassembler.Incoming in = reassembler.onBinEnd(payload);
                    if (in == null) return;
                    if (!in.complete()) {
                        l.onText("SERVER", from, "Binary transfer missing chunks (UDP loss). Ask sender to resend.");
                        return;
                    }
                    l.onBinary(in.kind, in.from, in.to, in.name, in.join());
                }
                case "VOICE_START" -> l.onVoiceStart(from, to, VoiceFormat.pcm());
                case "VOICE_CHUNK" -> {
                    String[] p = payload.split(";", 3);
                    if (p.length < 3) return;
                    byte[] chunk = Base64.getDecoder().decode(p[2]);
                    l.onVoiceChunk(from, to, chunk);
                }
                case "VOICE_END" -> l.onVoiceEnd(from, to);
                default -> {
                }
            }
        } catch (Exception ignored) {}
    }

    private void sendTo(String name, String msg) throws Exception {
        SocketAddress addr = clients.get(name);
        if (addr != null) send(addr, msg);
    }

    private void send(SocketAddress addr, String msg) throws Exception {
        byte[] data = UdpWire.bytes(msg);
        DatagramPacket pkt = new DatagramPacket(data, data.length);
        pkt.setSocketAddress(addr);
        socket.send(pkt);
    }

    @Override public void close() {
        running.set(false);
        if (socket != null) socket.close();
        socket = null;
        clients.clear();

        ServerControlListener l = listener;
        if (l != null) {
            try { l.onClientsChanged(List.of()); } catch (Exception ignored) {}
        }
    }
}
