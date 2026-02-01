package UDP.Server;

import common.ChatServerInterface;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP implementation of ChatServerInterface with Singleton pattern.
 * Contains the server logic without UI components.
 */
public class UdpChatServerCore implements ChatServerInterface {
    private static UdpChatServerCore instance;
    private static final Object lock = new Object();

    private DatagramSocket socket;
    private boolean running = false;
    private int port = -1;
    private Thread serverThread;

    private final Map<String, UdpClientHandler> clients = new ConcurrentHashMap<>();

    // Listeners
    private java.util.function.Consumer<List<String>> onClientListUpdated;
    private java.util.function.Consumer<String> onLog;

    private UdpChatServerCore() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance
     */
    public static UdpChatServerCore getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new UdpChatServerCore();
                }
            }
        }
        return instance;
    }

    /**
     * Check if an instance already exists (for preventing multiple instances)
     */
    public static boolean hasInstance() {
        return instance != null && instance.isRunning();
    }

    @Override
    public boolean start(int port) {
        synchronized (lock) {
            if (running) {
                log("Server is already running on port " + this.port);
                return false;
            }

            this.port = port;

            try {
                socket = new DatagramSocket(port);
                running = true;
                log("Server started on UDP port " + port);

                serverThread = new Thread(this::receiveLoop, "udp-server-thread");
                serverThread.setDaemon(false);
                serverThread.start();

                return true;
            } catch (SocketException e) {
                log("Failed to start server: " + e.getMessage());
                running = false;
                this.port = -1;
                return false;
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (!running) {
                return;
            }

            running = false;

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                log("Error closing server socket: " + e.getMessage());
            }

            List<String> clientNames = new ArrayList<>(clients.keySet());
            for (String name : clientNames) {
                UdpClientHandler handler = clients.get(name);
                if (handler != null) {
                    handler.send("KICK", "SERVER", name, "Server shutting down");
                }
            }
            clients.clear();

            log("Server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running && socket != null && !socket.isClosed();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public List<String> getConnectedClients() {
        List<String> names = new ArrayList<>();
        for (UdpClientHandler handler : clients.values()) {
            names.add(handler.getClientName());
        }
        Collections.sort(names);
        return names;
    }

    @Override
    public boolean kickClient(String clientName, String reason) {
        UdpClientHandler handler = clients.remove(clientName);
        if (handler == null)
            return false;
        String msg = reason == null ? "Kicked by server" : reason;
        handler.send("KICK", "SERVER", clientName, msg);
        log("KICK " + clientName + (reason == null ? "" : (" - " + reason)));
        notifyClientListUpdated();
        return true;
    }

    @Override
    public void setOnClientListUpdated(java.util.function.Consumer<List<String>> listener) {
        this.onClientListUpdated = listener;
    }

    @Override
    public void setOnLog(java.util.function.Consumer<String> listener) {
        this.onLog = listener;
    }

    @Override
    public void broadcastMessage(String message) {
        log(message);
        for (UdpClientHandler handler : clients.values()) {
            handler.send("MSG", "SERVER", "*", message);
        }
    }

    @Override
    public boolean sendPrivateMessage(String from, String to, String message) {
        UdpClientHandler handler = clients.get(to);
        if (handler == null)
            return false;
        handler.send("MSG", from, to, message);
        log("(Private) " + from + " -> " + to + ": " + message);
        return true;
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8);
                handleMessage(msg, packet.getSocketAddress());

            } catch (IOException e) {
                if (running) {
                    log("Server receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handleMessage(String raw, SocketAddress senderAddr) {
        // TYPE|FROM|TO|PAYLOAD
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4)
            return;

        String type = parts[0];
        String from = parts[1];
        String to = parts[2];
        String payload = parts[3];

        switch (type) {
            case "HELLO" -> {
                if (from == null || from.isBlank())
                    return;
                UdpClientHandler handler = new UdpClientHandler(this, from, senderAddr);
                clients.put(from, handler);
                log("HELLO from " + from + " @ " + senderAddr);
                notifyClientListUpdated();
            }
            case "LEAVE" -> {
                clients.remove(from);
                log("LEAVE " + from);
                notifyClientListUpdated();
            }

            // forward all supported types
            case "MSG", "IMG_START", "IMG_CHUNK", "IMG_END",
                    "FILE_START", "FILE_CHUNK", "FILE_END",
                    "VOICE_START", "VOICE_CHUNK", "VOICE_END" ->
                forward(type, from, to, payload, senderAddr);

            default -> {
                /* ignore */ }
        }
    }

    private void forward(String type, String from, String to, String payload, SocketAddress senderAddr) {
        if ("*".equals(to)) {
            for (Map.Entry<String, UdpClientHandler> entry : clients.entrySet()) {
                String targetName = entry.getKey();
                if (targetName.equals(from))
                    continue;
                UdpClientHandler handler = entry.getValue();
                handler.send(type, from, "*", payload);
            }
        } else {
            UdpClientHandler handler = clients.get(to);
            if (handler != null) {
                handler.send(type, from, to, payload);
            } else {
                sendRaw("MSG|SERVER|" + from + "|User '" + to + "' not online.", senderAddr);
            }
        }
    }

    private void notifyClientListUpdated() {
        if (onClientListUpdated != null) {
            List<String> clients = getConnectedClients();
            onClientListUpdated.accept(clients);
        }

        // Also broadcast client list to all clients
        List<String> names = new ArrayList<>(this.clients.keySet());
        Collections.sort(names);
        String list = String.join(",", names);
        String payload = "CLIENTS|SERVER|*|" + list;

        for (UdpClientHandler handler : this.clients.values()) {
            sendRaw(payload, handler.getAddress());
        }
    }

    void sendRaw(String message, SocketAddress addr) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setSocketAddress(addr);
            socket.send(packet);
        } catch (IOException e) {
            log("Server send error: " + e.getMessage());
        }
    }

    private void log(String message) {
        if (onLog != null) {
            onLog.accept(message);
        }
        System.out.println("[UDP Server] " + message);
    }
}
