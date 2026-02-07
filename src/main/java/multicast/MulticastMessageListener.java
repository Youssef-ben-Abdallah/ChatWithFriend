package multicast;

/**
 * Listener for multicast messages.
 */
public interface MulticastMessageListener {
    /**
     * Called when a text message is received.
     * @param message the received message
     */
    void onMessage(String message);

    /**
     * Called when an image is received.
     * @param filename the image filename
     * @param imageData the image bytes
     */
    default void onImage(String filename, byte[] imageData) {
        // Default implementation does nothing
    }
}
