package UDP.Server;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class UdpChatServerCore {

    private final int port;
    private DatagramSocket socket;

    // name -> address
    private final Map<String, SocketAddress> clients = new ConcurrentHashMap<>();
    private Consumer<List<String>> onClientsUpdate;
    private Consumer<String> onLog;

    public UdpChatServerCore(int port) {
        this.port = port;
    }

    public int getPort() { return port; }

    public void setOnClientsUpdate(Consumer<List<String>> cb) {
        this.onClientsUpdate = cb;
    }

    public void setOnLog(Consumer<String> cb) {
        this.onLog = cb;
    }

    private void log(String s) {
        if (onLog != null) onLog.accept(s);
        System.out.println(s); // no emojis/icons
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        log("Server started on UDP port " + port);

        Thread receiver = new Thread(this::receiveLoop, "server-receiver");
        receiver.setDaemon(false);
        receiver.start();
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleMessage(msg, packet.getSocketAddress());

            } catch (IOException e) {
                log("Server receive error: " + e.getMessage());
            }
        }
    }

    private void handleMessage(String raw, SocketAddress senderAddr) {
        // TYPE|FROM|TO|PAYLOAD
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) return;

        String type = parts[0];
        String from = parts[1];
        String to = parts[2];
        String payload = parts[3];

        switch (type) {
            case "HELLO" -> {
                if (from == null || from.isBlank()) return;
                clients.put(from, senderAddr);
                log("HELLO from " + from + " @ " + senderAddr);
                broadcastClientList();
            }
            case "LEAVE" -> {
                clients.remove(from);
                log("LEAVE " + from);
                broadcastClientList();
            }

            // forward all supported types
            case "MSG", "IMG_START", "IMG_CHUNK", "IMG_END",
                 "FILE_START", "FILE_CHUNK", "FILE_END",
                 "VOICE_START", "VOICE_CHUNK", "VOICE_END" ->
                    forward(type, from, to, payload, senderAddr);

            default -> { /* ignore */ }
        }
    }

    private void forward(String type, String from, String to, String payload, SocketAddress senderAddr) {
        if ("*".equals(to)) {
            for (Map.Entry<String, SocketAddress> entry : clients.entrySet()) {
                String targetName = entry.getKey();
                if (targetName.equals(from)) continue;
                send(type + "|" + from + "|*|" + payload, entry.getValue());
            }
        } else {
            SocketAddress targetAddr = clients.get(to);
            if (targetAddr != null) {
                send(type + "|" + from + "|" + to + "|" + payload, targetAddr);
            } else {
                send("MSG|SERVER|" + from + "|User '" + to + "' not online.", senderAddr);
            }
        }
    }

    private void broadcastClientList() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);

        if (onClientsUpdate != null) onClientsUpdate.accept(names);

        String list = String.join(",", names);
        String payload = "CLIENTS|SERVER|*|" + list;

        for (SocketAddress addr : clients.values()) {
            send(payload, addr);
        }
    }

    private void send(String message, SocketAddress addr) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setSocketAddress(addr);
            socket.send(packet);
        } catch (IOException e) {
            log("Server send error: " + e.getMessage());
        }
    }

    // =========================
    // KICK SUPPORT
    // =========================
    public boolean kickClient(String name, String reason) {
        SocketAddress addr = clients.remove(name);
        if (addr == null) return false;

        // Tell client to disconnect
        send("KICK|SERVER|" + name + "|" + (reason == null ? "Kicked by server" : reason), addr);

        log("KICK " + name + (reason == null ? "" : (" - " + reason)));
        broadcastClientList();
        return true;
    }

    public List<String> getConnectedClients() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        return names;
    }
}
