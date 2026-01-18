package TCP.Client;

import common.ChatClientInterface;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TCP implementation of ChatClientInterface.
 * Contains the networking logic without UI components.
 */
public class TcpChatClientCore implements ChatClientInterface {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String clientName;
    private boolean connected = false;
    private Thread listenThread;
    
    // Listeners
    private ChatClientInterface.MessageListener onMessageReceived;
    private ChatClientInterface.ImageListener onImageReceived;
    private ChatClientInterface.FileListener onFileReceived;
    private ChatClientInterface.AudioListener onAudioReceived;
    private java.util.function.Consumer<List<String>> onClientListUpdated;
    private java.util.function.Consumer<Boolean> onConnectionStatusChanged;
    
    private List<String> connectedClients = new ArrayList<>();

    @Override
    public boolean connect(String serverAddress, int port, String clientName) {
        if (connected) {
            return true;
        }
        
        this.clientName = clientName;
        
        try {
            socket = new Socket(serverAddress, port);
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeUTF("NAME:" + clientName);
            out.flush();
            
            connected = true;
            notifyConnectionStatusChanged(true);
            
            listenThread = new Thread(this::listenLoop, "tcp-client-listener");
            listenThread.setDaemon(true);
            listenThread.start();
            
            return true;
        } catch (IOException ex) {
            connected = false;
            notifyConnectionStatusChanged(false);
            cleanup();
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        connected = false;
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        
        cleanup();
        notifyConnectionStatusChanged(false);
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
        
        try {
            if (target == null || target.equals("All")) {
                out.writeUTF("MSG_ALL:" + message);
            } else {
                out.writeUTF("MSG_TO:" + target + ":" + message);
            }
            out.flush();
        } catch (IOException ex) {
            handleError("Failed to send message: " + ex.getMessage());
        }
    }

    @Override
    public void sendImage(byte[] imageData, String filename, String target) {
        if (!isConnected()) {
            return;
        }
        
        try {
            if (target == null || target.equals("All")) {
                out.writeUTF("IMG_ALL:" + clientName + ":" + filename + ":" + imageData.length);
            } else {
                out.writeUTF("IMG_TO:" + target + ":" + clientName + ":" + filename + ":" + imageData.length);
            }
            out.flush();
            out.write(imageData);
            out.flush();
        } catch (IOException ex) {
            handleError("Failed to send image: " + ex.getMessage());
        }
    }

    @Override
    public void sendFile(byte[] fileData, String filename, String target) {
        if (!isConnected()) {
            return;
        }
        
        try {
            if (target == null || target.equals("All")) {
                out.writeUTF("FILE_ALL:" + clientName + ":" + filename + ":" + fileData.length);
            } else {
                out.writeUTF("FILE_TO:" + target + ":" + clientName + ":" + filename + ":" + fileData.length);
            }
            out.flush();
            out.write(fileData);
            out.flush();
        } catch (IOException ex) {
            handleError("Failed to send file: " + ex.getMessage());
        }
    }

    @Override
    public void sendAudio(byte[] audioData, String filename, String target) {
        if (!isConnected()) {
            return;
        }
        
        try {
            if (target == null || target.equals("All")) {
                out.writeUTF("AUDIO_ALL:" + clientName + ":" + filename + ":" + audioData.length);
            } else {
                out.writeUTF("AUDIO_TO:" + target + ":" + clientName + ":" + filename + ":" + audioData.length);
            }
            out.flush();
            out.write(audioData);
            out.flush();
        } catch (IOException ex) {
            handleError("Failed to send audio: " + ex.getMessage());
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

    private void listenLoop() {
        try {
            while (connected) {
                String header = in.readUTF();
                
                if (header.startsWith("USER_LIST:")) {
                    String usersCsv = header.substring("USER_LIST:".length());
                    updateClientList(usersCsv);
                } else if (header.startsWith("IMG_ALL:") || header.startsWith("IMG_TO:")) {
                    handleImage(header);
                } else if (header.startsWith("PDF_ALL:") || header.startsWith("PDF_TO:")) {
                    handlePdf(header);
                } else if (header.startsWith("AUDIO_ALL:") || header.startsWith("AUDIO_TO:")) {
                    handleAudio(header);
                } else if (header.startsWith("FILE_ALL:") || header.startsWith("FILE_TO:")) {
                    handleFile(header);
                } else {
                    // Regular message - server sends "senderName: message" or "(Private) senderName: message"
                    if (onMessageReceived != null) {
                        boolean isPrivate = header.contains("(Private)");
                        String cleanHeader = header.replaceAll("^\\(Private\\)\\s*", "");
                        
                        // Extract sender and message
                        int colonIndex = cleanHeader.indexOf(":");
                        if (colonIndex > 0) {
                            String sender = cleanHeader.substring(0, colonIndex).trim();
                            String message = cleanHeader.substring(colonIndex + 1).trim();
                            onMessageReceived.onMessage(message, sender, isPrivate);
                        } else {
                            // No colon, treat entire string as message
                            onMessageReceived.onMessage(cleanHeader, "Unknown", isPrivate);
                        }
                    }
                }
            }
        } catch (java.io.EOFException eof) {
            // Connection closed by server
        } catch (IOException ex) {
            if (connected) {
                handleError("Connection error: " + ex.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void handleImage(String header) throws IOException {
        String[] parts = header.split(":", 5);
        boolean isAll = header.startsWith("IMG_ALL:");
        String sender, filename;
        int size;
        boolean isPrivate = false;
        
        if (isAll && parts.length >= 4) {
            sender = parts[1];
            filename = parts[2];
            size = Integer.parseInt(parts[3]);
        } else if (!isAll && parts.length >= 5) {
            String target = parts[1];
            sender = parts[2];
            filename = parts[3];
            size = Integer.parseInt(parts[4]);
            isPrivate = Objects.equals(target, clientName) || Objects.equals(target, "All");
        } else {
            return;
        }
        
        byte[] data = new byte[size];
        in.readFully(data);
        
        if (onImageReceived != null) {
            onImageReceived.onImage(data, filename, sender, isPrivate);
        }
    }

    private void handlePdf(String header) throws IOException {
        String[] parts = header.split(":", 5);
        boolean isAll = header.startsWith("PDF_ALL:");
        String sender, filename;
        int size;
        boolean isPrivate = false;
        
        if (isAll && parts.length >= 4) {
            sender = parts[1];
            filename = parts[2];
            size = Integer.parseInt(parts[3]);
        } else if (!isAll && parts.length >= 5) {
            String target = parts[1];
            sender = parts[2];
            filename = parts[3];
            size = Integer.parseInt(parts[4]);
            isPrivate = Objects.equals(target, clientName) || Objects.equals(target, "All");
        } else {
            return;
        }
        
        byte[] data = new byte[size];
        in.readFully(data);
        
        if (onFileReceived != null) {
            onFileReceived.onFile(data, filename, sender, isPrivate);
        }
    }

    private void handleFile(String header) throws IOException {
        String[] parts = header.split(":", 5);
        boolean isAll = header.startsWith("FILE_ALL:");
        String sender, filename;
        int size;
        boolean isPrivate = false;
        
        if (isAll && parts.length >= 4) {
            sender = parts[1];
            filename = parts[2];
            size = Integer.parseInt(parts[3]);
        } else if (!isAll && parts.length >= 5) {
            String target = parts[1];
            sender = parts[2];
            filename = parts[3];
            size = Integer.parseInt(parts[4]);
            isPrivate = Objects.equals(target, clientName) || Objects.equals(target, "All");
        } else {
            return;
        }
        
        byte[] data = new byte[size];
        in.readFully(data);
        
        if (onFileReceived != null) {
            onFileReceived.onFile(data, filename, sender, isPrivate);
        }
    }

    private void handleAudio(String header) throws IOException {
        String[] parts = header.split(":", 5);
        boolean isAll = header.startsWith("AUDIO_ALL:");
        String sender, filename;
        int size;
        boolean isPrivate = false;
        
        if (isAll && parts.length >= 4) {
            sender = parts[1];
            filename = parts[2];
            size = Integer.parseInt(parts[3]);
        } else if (!isAll && parts.length >= 5) {
            String target = parts[1];
            sender = parts[2];
            filename = parts[3];
            size = Integer.parseInt(parts[4]);
            isPrivate = Objects.equals(target, clientName) || Objects.equals(target, "All");
        } else {
            return;
        }
        
        byte[] data = new byte[size];
        in.readFully(data);
        
        if (onAudioReceived != null) {
            onAudioReceived.onAudio(data, filename, sender, isPrivate);
        }
    }

    private void updateClientList(String usersCsv) {
        connectedClients.clear();
        connectedClients.add("All");
        if (usersCsv != null && !usersCsv.trim().isEmpty()) {
            for (String u : usersCsv.split(",")) {
                u = u.trim();
                if (!u.isEmpty() && !u.equals(clientName)) {
                    connectedClients.add(u);
                }
            }
        }
        
        if (onClientListUpdated != null) {
            onClientListUpdated.accept(new ArrayList<>(connectedClients));
        }
    }


    private void notifyConnectionStatusChanged(boolean status) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(status);
        }
    }

    private void handleError(String message) {
        System.err.println("[TCP Client] " + message);
        // Could notify UI via a listener if needed
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
