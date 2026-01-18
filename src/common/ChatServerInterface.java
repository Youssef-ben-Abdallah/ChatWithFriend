package common;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for chat server implementations.
 * All servers (TCP and UDP) must implement this interface.
 */
public interface ChatServerInterface {

    /**
     * Start the server on the specified port
     * 
     * @param port Server port
     * @return true if server started successfully, false otherwise
     */
    boolean start(int port);

    /**
     * Stop the server
     */
    void stop();

    /**
     * Check if server is running
     * 
     * @return true if server is running
     */
    boolean isRunning();

    /**
     * Get the port the server is running on
     * 
     * @return port number, or -1 if not started
     */
    int getPort();

    /**
     * Get list of connected clients
     * 
     * @return List of client names
     */
    List<String> getConnectedClients();

    /**
     * Kick a client from the server
     * 
     * @param clientName Name of client to kick
     * @param reason     Reason for kicking (optional)
     * @return true if client was found and kicked
     */
    boolean kickClient(String clientName, String reason);

    /**
     * Set callback for when client list is updated
     * 
     * @param listener Callback that receives list of client names
     */
    void setOnClientListUpdated(Consumer<List<String>> listener);

    /**
     * Set callback for log messages
     * 
     * @param listener Callback that receives log message
     */
    void setOnLog(Consumer<String> listener);

    /**
     * Broadcast a message to all clients
     * 
     * @param message Message to broadcast
     */
    void broadcastMessage(String message);

    /**
     * Send a private message from one client to another
     * 
     * @param from    Sender client name
     * @param to      Receiver client name
     * @param message Message text
     * @return true if message was sent successfully
     */
    boolean sendPrivateMessage(String from, String to, String message);
}
