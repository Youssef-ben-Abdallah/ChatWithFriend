package tcp;

import core.model.BinaryKind;
import core.net.LogSink;
import core.net.ServerControlApi;
import core.net.ServerControlListener;
import core.util.IOUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI-free TCP server core.
 *
 * Frames:
 *  - TEXT:<from>:<to>:<message>
 *  - BIN:<kind>:<from>:<to>:<filename>:<size> + bytes
 *  - USER_LIST:<name1,name2,...>
 *  - VOICE_START:<from>:<to>:<sr>:<ch>:<bits>:<bigEndian>:<signed>
 *  - VOICE_CHUNK:<from>:<to>:<size> + bytes
 *  - VOICE_END:<from>:<to>
 *  - KICK:SERVER:<to>:<reason>
 *
 * Notes:
 * - One handler thread per client (pooled).
 * - Outgoing writes are synchronized per client session to avoid interleaving.
 */
public final class TcpServerCore implements ServerControlApi {

    private final int port;
    private final LogSink log;

    private volatile ServerControlListener listener;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutorService clientPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // name -> session
    private final ConcurrentHashMap<String, ClientSession> clients = new ConcurrentHashMap<>();

    public TcpServerCore(int port, LogSink log) {
        this.port = port;
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    @Override public void setListener(ServerControlListener listener) {
        this.listener = listener;
    }

    @Override public void start() throws Exception {
        if (running.get()) return;
        serverSocket = new ServerSocket(port);
        running.set(true);

        acceptThread = new Thread(this::acceptLoop, "TcpAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.log("[TCP] Server listening on " + port);
    }

    @Override public boolean isRunning() { return running.get(); }

    @Override public List<String> getClients() {
        List<String> names = new ArrayList<>(clients.keySet());
        Collections.sort(names);
        return names;
    }

    @Override public void kick(String name, String reason) {
        if (name == null || name.isBlank()) return;

        ClientSession s = clients.remove(name);
        if (s == null) return;

        String r = (reason == null || reason.isBlank()) ? "Removed by server" : reason.trim();
        try {
            s.sendHeader("KICK:SERVER:" + name + ":" + r);
        } catch (Exception ignored) {}
        try { s.close(); } catch (Exception ignored) {}

        log.log("[TCP] Kicked " + name + " (" + r + ")");
        broadcastUserList(); // will notify UI too
    }

    private void acceptLoop() {
        try {
            while (running.get()) {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                clientPool.submit(() -> handleClient(s));
            }
        } catch (Exception e) {
            if (running.get()) log.log("[TCP] Accept error: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleClient(Socket socket) {
        String clientName = null;

        try (Socket s = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

            // handshake
            String hello = in.readUTF();
            if (!hello.startsWith("HELLO:")) return;
            clientName = hello.substring("HELLO:".length()).trim();
            if (clientName.isEmpty()) return;

            // prevent name clash
            if (clients.containsKey(clientName)) {
                out.writeUTF("TEXT:SERVER:" + clientName + ":Name already in use");
                out.flush();
                return;
            }

            ClientSession session = new ClientSession(clientName, s, out);
            clients.put(clientName, session);

            log.log("[TCP] " + clientName + " connected (" + s.getRemoteSocketAddress() + ")");
            broadcastUserList();

            // read frames
            while (running.get() && !s.isClosed()) {
                String header = in.readUTF();

                if (header.startsWith("TEXT:")) {
                    // TEXT:<from>:<to>:<message>
                    String[] p = header.split(":", 4);
                    if (p.length != 4) continue;
                    routeText(p[1], p[2], p[3]);
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
                    routeBinary(kind, from, to, fileName, bytes);
                    continue;
                }

                if (header.startsWith("VOICE_START:")) {
                    // forward header as-is (clients parse format themselves)
                    String[] p = header.split(":", 4);
                    if (p.length < 3) continue;
                    routeHeader(header, p[2]);
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
                    routeVoiceChunk(from, to, bytes);
                    continue;
                }

                if (header.startsWith("VOICE_END:")) {
                    // VOICE_END:<from>:<to>
                    String[] p = header.split(":", 3);
                    if (p.length != 3) continue;
                    routeHeader(header, p[2]);
                    continue;
                }

                // ignore unknown frames
            }

        } catch (Exception e) {
            if (clientName != null) log.log("[TCP] Client " + clientName + " error: " + e.getMessage());
        } finally {
            if (clientName != null) {
                clients.remove(clientName);
                broadcastUserList();
                log.log("[TCP] " + clientName + " disconnected");
            }
        }
    }

    private void routeText(String from, String to, String message) {
        String frame = "TEXT:" + from + ":" + to + ":" + message;

        if ("*".equals(to)) {
            broadcast(frame);
        } else {
            sendTo(to, frame);
            // echo to sender if private
            sendTo(from, frame);
        }
    }

    private void routeBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes) {
        if ("*".equals(to)) {
            for (ClientSession c : clients.values()) {
                try { c.sendBinary(kind, from, to, fileName, bytes); } catch (Exception ignored) {}
            }
        } else {
            ClientSession target = clients.get(to);
            ClientSession sender = clients.get(from);
            try { if (target != null) target.sendBinary(kind, from, to, fileName, bytes); } catch (Exception ignored) {}
            try { if (sender != null) sender.sendBinary(kind, from, to, fileName, bytes); } catch (Exception ignored) {}
        }
    }

    private void routeHeader(String header, String to) {
        if ("*".equals(to)) {
            broadcast(header);
        } else {
            sendTo(to, header);

            // echo to sender
            String[] p = header.split(":", 3);
            if (p.length >= 2) sendTo(p[1], header);
        }
    }

    private void routeVoiceChunk(String from, String to, byte[] bytes) {
        if ("*".equals(to)) {
            for (ClientSession c : clients.values()) {
                try { c.sendVoiceChunk(from, to, bytes); } catch (Exception ignored) {}
            }
        } else {
            ClientSession target = clients.get(to);
            ClientSession sender = clients.get(from);
            try { if (target != null) target.sendVoiceChunk(from, to, bytes); } catch (Exception ignored) {}
            try { if (sender != null) sender.sendVoiceChunk(from, to, bytes); } catch (Exception ignored) {}
        }
    }

    private void broadcast(String header) {
        for (ClientSession c : clients.values()) {
            try { c.sendHeader(header); } catch (Exception ignored) {}
        }
    }

    private void sendTo(String name, String header) {
        ClientSession c = clients.get(name);
        if (c == null) return;
        try { c.sendHeader(header); } catch (Exception ignored) {}
    }

    private void broadcastUserList() {
        List<String> names = getClients();
        String frame = "USER_LIST:" + String.join(",", names);

        broadcast(frame);

        ServerControlListener l = listener;
        if (l != null) {
            try { l.onClientsChanged(names); } catch (Exception ignored) {}
        }
    }

    @Override public void close() {
        running.set(false);
        IOUtil.closeQuietly(serverSocket);
        serverSocket = null;

        // Best-effort close all sessions
        for (ClientSession s : clients.values()) {
            try { s.close(); } catch (Exception ignored) {}
        }
        clients.clear();

        clientPool.shutdownNow();
        ServerControlListener l = listener;
        if (l != null) {
            try { l.onClientsChanged(List.of()); } catch (Exception ignored) {}
        }
    }

    private static final class ClientSession implements Closeable {
        final String name;
        final Socket socket;
        final DataOutputStream out;

        ClientSession(String name, Socket socket, DataOutputStream out) {
            this.name = name;
            this.socket = socket;
            this.out = out;
        }

        synchronized void sendHeader(String header) throws IOException {
            out.writeUTF(header);
            out.flush();
        }

        synchronized void sendBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes) throws IOException {
            out.writeUTF("BIN:" + kind + ":" + from + ":" + to + ":" + fileName + ":" + bytes.length);
            out.write(bytes);
            out.flush();
        }

        synchronized void sendVoiceChunk(String from, String to, byte[] bytes) throws IOException {
            out.writeUTF("VOICE_CHUNK:" + from + ":" + to + ":" + bytes.length);
            out.write(bytes);
            out.flush();
        }

        @Override public void close() throws IOException {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
