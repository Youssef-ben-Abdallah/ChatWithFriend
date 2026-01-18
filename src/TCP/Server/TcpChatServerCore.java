    package TCP.Server;

import TCP.Client.ClientHandler;
import common.ChatServerInterface;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP implementation of ChatServerInterface with Singleton pattern.
 * Contains the server logic without UI components.
 */
public class TcpChatServerCore implements ChatServerInterface {
    private static TcpChatServerCore instance;
    private static final Object lock = new Object();

    private ServerSocket serverSocket;
    private final Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();
    private boolean running = false;
    private int port = -1;
    private Thread serverThread;

    // Listeners
    private java.util.function.Consumer<List<String>> onClientListUpdated;
    private java.util.function.Consumer<String> onLog;

    private TcpChatServerCore() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance
     * 
     * @return The singleton instance
     */
    public static TcpChatServerCore getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new TcpChatServerCore();
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
                serverSocket = new ServerSocket(port);
                running = true;
                log("Server started on port " + port);

                serverThread = new Thread(this::serverLoop, "tcp-server-thread");
                serverThread.setDaemon(false);
                serverThread.start();

                return true;
            } catch (IOException e) {
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
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }

            // Disconnect all clients
            List<ClientHandler> handlers = new ArrayList<>(clientHandlers);
            for (ClientHandler handler : handlers) {
                try {
                    handler.getSocket().close();
                } catch (Exception ignored) {
                }
            }
            clientHandlers.clear();

            log("Server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public List<String> getConnectedClients() {
        List<String> clients = new ArrayList<>();
        for (ClientHandler ch : clientHandlers) {
            String name = ch.getClientName();
            if (name != null) {
                clients.add(name);
            }
        }
        return clients;
    }

    @Override
    public boolean kickClient(String clientName, String reason) {
        ClientHandler toKick = null;
        for (ClientHandler ch : clientHandlers) {
            if (clientName.equals(ch.getClientName())) {
                toKick = ch;
                break;
            }
        }

        if (toKick != null) {
            try {
                toKick.getSocket().close();
            } catch (IOException e) {
                log("Error kicking client: " + e.getMessage());
            }
            clientHandlers.remove(toKick);
            notifyClientListUpdated();
            log("Kicked client: " + clientName + (reason != null ? " - " + reason : ""));
            return true;
        }
        return false;
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
        for (ClientHandler ch : clientHandlers) {
            ch.sendMessage(message);
        }
    }

    @Override
    public boolean sendPrivateMessage(String from, String to, String message) {
        for (ClientHandler ch : clientHandlers) {
            if (to.equals(ch.getClientName())) {
                ch.sendMessage("(Private) " + from + ": " + message);
                log("(Private) " + from + " -> " + to + ": " + message);
                return true;
            }
        }
        return false;
    }

    private void serverLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                log("New client connected: " + socket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(socket, clientHandlers, this);
                clientHandlers.add(handler);
                notifyClientListUpdated();

                Thread clientThread = new Thread(handler, "client-handler-" + socket.getRemoteSocketAddress());
                clientThread.setDaemon(true);
                clientThread.start();

            } catch (IOException e) {
                if (running) {
                    log("Server accept error: " + e.getMessage());
                }
            }
        }
    }

    // Methods for ClientHandler to call (need to expose for backwards
    // compatibility)
    public void onClientConnected(String clientName) {
        log(clientName + " connected");
        notifyClientListUpdated();
    }

    public void onClientDisconnected(String clientName) {
        log(clientName + " disconnected");
        notifyClientListUpdated();
    }

    public void broadcast(String message, ClientHandler sender) {
        log(message);
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) {
                ch.sendMessage(message);
            }
        }
    }

    public void broadcastSystem(String systemMessage, ClientHandler exclude) {
        log(systemMessage);
        for (ClientHandler ch : clientHandlers) {
            if (ch != exclude) {
                ch.sendMessage(systemMessage);
            }
        }
    }

    public void sendClientList() {
        StringBuilder sb = new StringBuilder("USER_LIST:");
        for (ClientHandler ch : clientHandlers) {
            String n = ch.getClientName();
            if (n != null)
                sb.append(n).append(",");
        }
        if (sb.length() > "USER_LIST:".length()) {
            sb.setLength(sb.length() - 1);
        }
        String header = sb.toString();
        for (ClientHandler ch : clientHandlers) {
            ch.sendMessage(header);
        }
    }

    // File transfer methods (for ClientHandler compatibility)
    public void broadcastImageAll(String senderName, String filename, byte[] img, ClientHandler sender) {
        log(senderName + " sent an image to All: " + filename);
        String header = "IMG_ALL:" + senderName + ":" + filename + ":" + img.length;
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender)
                ch.sendImage(header, img);
        }
    }

    public void sendPrivateImage(String target, String senderName, String filename, byte[] img, ClientHandler sender) {
        String header = "IMG_TO:" + target + ":" + senderName + ":" + filename + ":" + img.length;
        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendImage(header, img);
                log(senderName + " sent a private image to " + target + ": " + filename);
                return;
            }
        }
        sender.sendMessage("⚠️ User '" + target + "' not found.");
    }

    public void broadcastPdfAll(String senderName, String filename, byte[] pdf, ClientHandler sender) {
        log(senderName + " sent a PDF to All: " + filename);
        String header = "PDF_ALL:" + senderName + ":" + filename + ":" + pdf.length;
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender)
                ch.sendFile(header, pdf);
        }
    }

    public void sendPrivatePdf(String target, String senderName, String filename, byte[] pdf, ClientHandler sender) {
        String header = "PDF_TO:" + target + ":" + senderName + ":" + filename + ":" + pdf.length;
        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, pdf);
                log(senderName + " sent a private PDF to " + target + ": " + filename);
                return;
            }
        }
        sender.sendMessage("⚠️ User '" + target + "' not found.");
    }

    public void broadcastFileAll(String senderName, String filename, byte[] fileData, ClientHandler sender) {
        log(senderName + " sent a file to All: " + filename);
        String header = "FILE_ALL:" + senderName + ":" + filename + ":" + fileData.length;
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender)
                ch.sendFile(header, fileData);
        }
    }

    public void sendPrivateFile(String target, String senderName, String filename, byte[] fileData,
            ClientHandler sender) {
        String header = "FILE_TO:" + target + ":" + senderName + ":" + filename + ":" + fileData.length;
        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, fileData);
                log(senderName + " sent a private file to " + target + ": " + filename);
                return;
            }
        }
        sender.sendMessage("⚠️ User '" + target + "' not found.");
    }

    public void broadcastAudioAll(String senderName, String filename, byte[] audio, ClientHandler sender) {
        log(senderName + " sent a voice message to All: " + filename);
        String header = "AUDIO_ALL:" + senderName + ":" + filename + ":" + audio.length;
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender)
                ch.sendFile(header, audio);
        }
    }

    public void sendPrivateAudio(String target, String senderName, String filename, byte[] audio,
            ClientHandler sender) {
        String header = "AUDIO_TO:" + target + ":" + senderName + ":" + filename + ":" + audio.length;
        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, audio);
                log(senderName + " sent a private voice message to " + target + ": " + filename);
                return;
            }
        }
        sender.sendMessage("⚠️ User '" + target + "' not found.");
    }

    public void sendPrivateMessage(String from, String to, String msg, ClientHandler sender) {
        for (ClientHandler ch : clientHandlers) {
            if (to.equals(ch.getClientName())) {
                ch.sendMessage("(Private) " + from + ": " + msg);
                log("(Private) " + from + " -> " + to + ": " + msg);
                return;
            }
        }
        sender.sendMessage("⚠️ User '" + to + "' not found.");
    }

    private void notifyClientListUpdated() {
        if (onClientListUpdated != null) {
            List<String> clients = getConnectedClients();
            onClientListUpdated.accept(clients);
        }
    }

    private void log(String message) {
        if (onLog != null) {
            onLog.accept(message);
        }
        System.out.println("[TCP Server] " + message);
    }
}
