package UDP.Client;

import common.ChatClientInterface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class UdpChatClientCore implements ChatClientInterface {
    private static final String ALL = "All";

    private DatagramSocket socket;
    private InetAddress serverAddr;
    private int serverPort;
    private String clientName;
    private volatile boolean connected = false;
    private Thread receiveThread;

    private ChatClientInterface.MessageListener onMessageReceived;
    private ChatClientInterface.ImageListener onImageReceived;
    private ChatClientInterface.FileListener onFileReceived;
    private ChatClientInterface.AudioListener onAudioReceived;
    private java.util.function.Consumer<List<String>> onClientListUpdated;
    private java.util.function.Consumer<Boolean> onConnectionStatusChanged;

    private final List<String> connectedClients = new ArrayList<>();

    private static class IncomingImage {
        String from;
        String fileName;
        int total;
        byte[][] parts;
        boolean[] got;
    }

    private static class IncomingFile {
        String from;
        String fileName;
        int total;
        byte[][] parts;
        boolean[] got;
    }

    private static class IncomingVoice {
        String from;
        int total;
        byte[][] parts;
        boolean[] got;
    }

    private final java.util.Map<String, IncomingImage> incomingImages = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, IncomingFile> incomingFiles = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, IncomingVoice> incomingVoices = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean connect(String serverAddress, int port, String clientName) {
        if (connected) {
            return true;
        }

        this.clientName = clientName;
        this.serverPort = port;

        try {
            serverAddr = InetAddress.getByName(serverAddress);
            socket = new DatagramSocket();
            connected = true;
            notifyConnectionStatusChanged(true);

            receiveThread = new Thread(this::receiveLoop, "udp-client-receiver");
            receiveThread.setDaemon(true);
            receiveThread.start();

            sendRaw("HELLO|" + clientName + "|*|hi");
            return true;
        } catch (Exception e) {
            handleError("Connect failed: " + e.getMessage());
            connected = false;
            notifyConnectionStatusChanged(false);
            cleanup();
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (!connected) {
            notifyConnectionStatusChanged(false);
            return;
        }

        try {
            sendRaw("LEAVE|" + clientName + "|*|bye");
        } catch (Exception ignored) {
        }

        connected = false;
        notifyConnectionStatusChanged(false);

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }

        cleanup();
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    @Override
    public String getClientName() {
        return clientName;
    }

    @Override
    public void sendMessage(String message, String target) {
        if (!isConnected()) {
            return;
        }

        String to = normalizeTarget(target);
        try {
            sendRaw("MSG|" + clientName + "|" + to + "|" + message);
        } catch (Exception e) {
            handleError("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void sendImage(byte[] imageData, String filename, String target) {
        if (!isConnected()) {
            return;
        }

        String to = normalizeTarget(target);

        try {
            String transferId = UUID.randomUUID().toString();
            int rawChunkSize = 400;
            int totalChunks = (int) Math.ceil(imageData.length / (double) rawChunkSize);

            for (int k = 0; k < 3; k++) {
                sendRaw("IMG_START|" + clientName + "|" + to + "|" + transferId + ";" + filename + ";" + totalChunks);
                sleepMs(10);
            }

            Base64.Encoder enc = Base64.getEncoder();
            for (int i = 0; i < totalChunks; i++) {
                int start = i * rawChunkSize;
                int end = Math.min(imageData.length, start + rawChunkSize);
                byte[] part = java.util.Arrays.copyOfRange(imageData, start, end);

                String b64 = enc.encodeToString(part);
                sendRaw("IMG_CHUNK|" + clientName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
                sleepMs(2);
            }

            for (int k = 0; k < 3; k++) {
                sendRaw("IMG_END|" + clientName + "|" + to + "|" + transferId);
                sleepMs(10);
            }
        } catch (Exception e) {
            handleError("Failed to send image: " + e.getMessage());
        }
    }

    @Override
    public void sendFile(byte[] fileData, String filename, String target) {
        if (!isConnected()) {
            return;
        }

        String to = normalizeTarget(target);

        try {
            String transferId = UUID.randomUUID().toString();
            int rawChunkSize = 400;
            int totalChunks = (int) Math.ceil(fileData.length / (double) rawChunkSize);

            for (int k = 0; k < 3; k++) {
                sendRaw("FILE_START|" + clientName + "|" + to + "|" + transferId + ";" + filename + ";" + totalChunks);
                sleepMs(10);
            }

            Base64.Encoder enc = Base64.getEncoder();
            for (int i = 0; i < totalChunks; i++) {
                int start = i * rawChunkSize;
                int end = Math.min(fileData.length, start + rawChunkSize);
                byte[] part = java.util.Arrays.copyOfRange(fileData, start, end);

                String b64 = enc.encodeToString(part);
                sendRaw("FILE_CHUNK|" + clientName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
                sleepMs(2);
            }

            for (int k = 0; k < 3; k++) {
                sendRaw("FILE_END|" + clientName + "|" + to + "|" + transferId);
                sleepMs(10);
            }
        } catch (Exception e) {
            handleError("Failed to send file: " + e.getMessage());
        }
    }

    @Override
    public void sendAudio(byte[] audioData, String filename, String target) {
        if (!isConnected()) {
            return;
        }

        String to = normalizeTarget(target);

        try {
            String transferId = UUID.randomUUID().toString();
            int rawChunkSize = 400;
            int totalChunks = (int) Math.ceil(audioData.length / (double) rawChunkSize);

            for (int k = 0; k < 3; k++) {
                sendRaw("VOICE_START|" + clientName + "|" + to + "|" + transferId + ";" + totalChunks);
                sleepMs(10);
            }

            Base64.Encoder enc = Base64.getEncoder();
            for (int i = 0; i < totalChunks; i++) {
                int start = i * rawChunkSize;
                int end = Math.min(audioData.length, start + rawChunkSize);
                byte[] part = java.util.Arrays.copyOfRange(audioData, start, end);

                String b64 = enc.encodeToString(part);
                sendRaw("VOICE_CHUNK|" + clientName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
                sleepMs(2);
            }

            for (int k = 0; k < 3; k++) {
                sendRaw("VOICE_END|" + clientName + "|" + to + "|" + transferId);
                sleepMs(10);
            }
        } catch (Exception e) {
            handleError("Failed to send audio: " + e.getMessage());
        }
    }

    @Override
    public List<String> getConnectedClients() {
        return new ArrayList<>(connectedClients);
    }

    @Override
    public void setOnMessageReceived(ChatClientInterface.MessageListener listener) {
        this.onMessageReceived = listener;
    }

    @Override
    public void setOnImageReceived(ChatClientInterface.ImageListener listener) {
        this.onImageReceived = listener;
    }

    @Override
    public void setOnFileReceived(ChatClientInterface.FileListener listener) {
        this.onFileReceived = listener;
    }

    @Override
    public void setOnAudioReceived(ChatClientInterface.AudioListener listener) {
        this.onAudioReceived = listener;
    }

    @Override
    public void setOnClientListUpdated(java.util.function.Consumer<List<String>> listener) {
        this.onClientListUpdated = listener;
    }

    @Override
    public void setOnConnectionStatusChanged(java.util.function.Consumer<Boolean> listener) {
        this.onConnectionStatusChanged = listener;
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507];

        while (connected && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleIncoming(msg);
            } catch (IOException e) {
                if (connected) {
                    handleError("Receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncoming(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) {
            return;
        }

        String type = parts[0];
        String from = parts[1];
        String to = parts[2];
        String payload = parts[3];

        switch (type) {
            case "CLIENTS" -> updateClientList(payload);
            case "MSG" -> handleMessage(from, to, payload);
            case "KICK" -> {
                handleError("Kicked by server: " + payload);
                disconnect();
            }
            case "IMG_START" -> handleImgStart(from, payload);
            case "IMG_CHUNK" -> handleImgChunk(payload);
            case "IMG_END" -> handleImgEnd(payload);
            case "FILE_START" -> handleFileStart(from, payload);
            case "FILE_CHUNK" -> handleFileChunk(payload);
            case "FILE_END" -> handleFileEnd(payload);
            case "VOICE_START" -> handleVoiceStart(from, payload);
            case "VOICE_CHUNK" -> handleVoiceChunk(payload);
            case "VOICE_END" -> handleVoiceEnd(payload);
            default -> {
            }
        }
    }

    private void handleMessage(String from, String to, String payload) {
        if (onMessageReceived == null) {
            return;
        }

        boolean isPrivate = !"*".equals(to);
        onMessageReceived.onMessage(payload, from, isPrivate);
    }

    private void updateClientList(String csv) {
        connectedClients.clear();
        connectedClients.add(ALL);

        if (csv != null && !csv.isBlank()) {
            String[] names = csv.split(",");
            for (String n : names) {
                String name = n.trim();
                if (!name.isEmpty() && !name.equals(clientName)) {
                    connectedClients.add(name);
                }
            }
        }

        if (onClientListUpdated != null) {
            onClientListUpdated.accept(new ArrayList<>(connectedClients));
        }
    }

    private void handleImgStart(String from, String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) {
            return;
        }

        String id = p[0];
        String fileName = p[1];

        int total;
        try {
            total = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingImage img = new IncomingImage();
        img.from = from;
        img.fileName = fileName;
        img.total = total;
        img.parts = new byte[total][];
        img.got = new boolean[total];

        incomingImages.put(id, img);
    }

    private void handleImgChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) {
            return;
        }

        String id = p[0];
        int idx;
        try {
            idx = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingImage img = incomingImages.get(id);
        if (img == null || idx < 0 || idx >= img.total || img.got[idx]) {
            return;
        }

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            img.parts[idx] = part;
            img.got[idx] = true;
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleImgEnd(String payload) {
        String id = payload.trim();
        IncomingImage img = incomingImages.get(id);
        if (img == null) {
            return;
        }

        for (boolean b : img.got) {
            if (!b) {
                incomingImages.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < img.total; i++) {
                full.write(img.parts[i]);
            }
            byte[] bytes = full.toByteArray();

            if (onImageReceived != null) {
                boolean isPrivate = false;
                onImageReceived.onImage(bytes, img.fileName, img.from, isPrivate);
            }
        } catch (Exception e) {
            handleError("Failed to rebuild image: " + e.getMessage());
        } finally {
            incomingImages.remove(id);
        }
    }

    private void handleFileStart(String from, String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) {
            return;
        }

        String id = p[0];
        String fileName = p[1];
        int total;
        try {
            total = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingFile f = new IncomingFile();
        f.from = from;
        f.fileName = fileName;
        f.total = total;
        f.parts = new byte[total][];
        f.got = new boolean[total];

        incomingFiles.put(id, f);
    }

    private void handleFileChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) {
            return;
        }

        String id = p[0];
        int idx;
        try {
            idx = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingFile f = incomingFiles.get(id);
        if (f == null || idx < 0 || idx >= f.total || f.got[idx]) {
            return;
        }

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            f.parts[idx] = part;
            f.got[idx] = true;
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleFileEnd(String payload) {
        String id = payload.trim();
        IncomingFile f = incomingFiles.get(id);
        if (f == null) {
            return;
        }

        for (boolean b : f.got) {
            if (!b) {
                incomingFiles.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < f.total; i++) {
                full.write(f.parts[i]);
            }
            byte[] bytes = full.toByteArray();

            if (onFileReceived != null) {
                boolean isPrivate = false;
                onFileReceived.onFile(bytes, f.fileName, f.from, isPrivate);
            }
        } catch (Exception e) {
            handleError("Failed to rebuild file: " + e.getMessage());
        } finally {
            incomingFiles.remove(id);
        }
    }

    private void handleVoiceStart(String from, String payload) {
        String[] p = payload.split(";", 2);
        if (p.length < 2) {
            return;
        }

        String id = p[0];
        int total;
        try {
            total = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingVoice v = new IncomingVoice();
        v.from = from;
        v.total = total;
        v.parts = new byte[total][];
        v.got = new boolean[total];

        incomingVoices.put(id, v);
    }

    private void handleVoiceChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) {
            return;
        }

        String id = p[0];
        int idx;
        try {
            idx = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return;
        }

        IncomingVoice v = incomingVoices.get(id);
        if (v == null || idx < 0 || idx >= v.total || v.got[idx]) {
            return;
        }

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            v.parts[idx] = part;
            v.got[idx] = true;
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleVoiceEnd(String payload) {
        String id = payload.trim();
        IncomingVoice v = incomingVoices.get(id);
        if (v == null) {
            return;
        }

        for (boolean b : v.got) {
            if (!b) {
                incomingVoices.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < v.total; i++) {
                full.write(v.parts[i]);
            }
            byte[] bytes = full.toByteArray();

            if (onAudioReceived != null) {
                boolean isPrivate = false;
                onAudioReceived.onAudio(bytes, "voice_" + System.currentTimeMillis() + ".wav", v.from, isPrivate);
            }
        } catch (Exception e) {
            handleError("Failed to rebuild audio: " + e.getMessage());
        } finally {
            incomingVoices.remove(id);
        }
    }

    private void sendRaw(String text) throws IOException {
        if (socket == null || serverAddr == null) {
            throw new SocketException("Socket not initialized");
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, serverPort);
        socket.send(packet);
    }

    private String normalizeTarget(String target) {
        if (target == null || ALL.equals(target)) {
            return "*";
        }
        return target;
    }

    private void notifyConnectionStatusChanged(boolean status) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(status);
        }
    }

    private void handleError(String message) {
        System.err.println("[UDP Client] " + message);
    }

    private void cleanup() {
        incomingImages.clear();
        incomingFiles.clear();
        incomingVoices.clear();
        connectedClients.clear();
    }

    private static void sleepMs(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}

