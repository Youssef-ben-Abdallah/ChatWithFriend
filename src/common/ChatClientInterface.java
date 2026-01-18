package common;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for chat client implementations.
 * All clients (TCP and UDP) must implement this interface.
 */
public interface ChatClientInterface {

    /**
     * Connect to the server
     * 
     * @param serverAddress Server address
     * @param port          Server port
     * @param clientName    Client name
     * @return true if connection successful, false otherwise
     */
    boolean connect(String serverAddress, int port, String clientName);

    /**
     * Disconnect from the server
     */
    void disconnect();

    /**
     * Check if client is connected
     * 
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Get the client name
     * 
     * @return client name
     */
    String getClientName();

    /**
     * Send a text message
     * 
     * @param message Message text
     * @param target  Target client name or "All" for broadcast
     */
    void sendMessage(String message, String target);

    /**
     * Send an image file
     * 
     * @param imageData Image bytes
     * @param filename  Image filename
     * @param target    Target client name or "All" for broadcast
     */
    void sendImage(byte[] imageData, String filename, String target);

    /**
     * Send a file
     * 
     * @param fileData File bytes
     * @param filename File name
     * @param target   Target client name or "All" for broadcast
     */
    void sendFile(byte[] fileData, String filename, String target);

    /**
     * Send an audio file
     * 
     * @param audioData Audio bytes
     * @param filename  Audio filename
     * @param target    Target client name or "All" for broadcast
     */
    void sendAudio(byte[] audioData, String filename, String target);

    /**
     * Get list of connected clients from server
     * 
     * @return List of client names
     */
    List<String> getConnectedClients();

    /**
     * Set callback for when a text message is received
     * 
     * @param listener Callback that receives (message, sender, isPrivate)
     */
    void setOnMessageReceived(MessageListener listener);

    /**
     * Set callback for when an image is received
     * 
     * @param listener Callback that receives (imageData, filename, sender,
     *                 isPrivate)
     */
    void setOnImageReceived(ImageListener listener);

    /**
     * Set callback for when a file is received
     * 
     * @param listener Callback that receives (fileData, filename, sender,
     *                 isPrivate)
     */
    void setOnFileReceived(FileListener listener);

    /**
     * Set callback for when audio is received
     * 
     * @param listener Callback that receives (audioData, filename, sender,
     *                 isPrivate)
     */
    void setOnAudioReceived(AudioListener listener);

    /**
     * Set callback for when client list is updated
     * 
     * @param listener Callback that receives list of client names
     */
    void setOnClientListUpdated(Consumer<List<String>> listener);

    /**
     * Set callback for connection status changes
     * 
     * @param listener Callback that receives connection status
     */
    void setOnConnectionStatusChanged(Consumer<Boolean> listener);

    // Listener interfaces for type safety
    @FunctionalInterface
    interface MessageListener {
        void onMessage(String message, String sender, boolean isPrivate);
    }

    @FunctionalInterface
    interface ImageListener {
        void onImage(byte[] imageData, String filename, String sender, boolean isPrivate);
    }

    @FunctionalInterface
    interface FileListener {
        void onFile(byte[] fileData, String filename, String sender, boolean isPrivate);
    }

    @FunctionalInterface
    interface AudioListener {
        void onAudio(byte[] audioData, String filename, String sender, boolean isPrivate);
    }
}
