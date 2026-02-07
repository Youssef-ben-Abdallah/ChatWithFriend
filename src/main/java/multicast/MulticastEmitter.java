package multicast;

import core.net.LogSink;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multicast emitter (sender).
 * Sends messages to a multicast group.
 */
public final class MulticastEmitter {
    private final String multicastAddress;
    private final int port;
    private final LogSink log;

    private MulticastSocket socket;
    private InetAddress group;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // UDP max packet size is ~65KB, but we'll use 60KB to be safe
    private static final int MAX_PACKET_SIZE = 60_000;

    public MulticastEmitter(String multicastAddress, int port, LogSink log) {
        this.multicastAddress = multicastAddress;
        this.port = port;
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    /**
     * Start the emitter.
     */
    public void start() throws Exception {
        if (running.get()) return;

        group = InetAddress.getByName(multicastAddress);
        socket = new MulticastSocket();
        socket.setTimeToLive(1); // Local network only

        running.set(true);
        log.log("[MULTICAST] Emitter started on " + multicastAddress + ":" + port);
    }

    /**
     * Send a text message to the multicast group.
     */
    public void send(String message) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("Emitter not started");
        }

        String payload = "TEXT:" + message;
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
            data,
            data.length,
            group,
            port
        );
        socket.send(packet);
        log.log("[MULTICAST] Sent text: " + message);
    }

    /**
     * Send an image file to the multicast group.
     */
    public void sendImage(File imageFile) throws Exception {
        if (!running.get()) {
            throw new IllegalStateException("Emitter not started");
        }

        if (imageFile == null || !imageFile.exists()) {
            throw new IOException("Image file not found");
        }

        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String filename = imageFile.getName();
        
        // Encode image as Base64
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        
        // Check if we need to chunk (Base64 increases size by ~33%)
        String payload = "IMAGE:" + filename + ":" + base64Image;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        
        if (payloadBytes.length > MAX_PACKET_SIZE) {
            // Chunk the image
            sendImageChunked(filename, imageBytes);
        } else {
            // Send in one packet
            DatagramPacket packet = new DatagramPacket(
                payloadBytes,
                payloadBytes.length,
                group,
                port
            );
            socket.send(packet);
            log.log("[MULTICAST] Sent image: " + filename + " (" + imageBytes.length + " bytes)");
        }
    }

    private void sendImageChunked(String filename, byte[] imageBytes) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        int chunkSize = MAX_PACKET_SIZE - 1000; // Leave room for header
        int totalChunks = (int) Math.ceil(base64Image.length() / (double) chunkSize);
        String id = filename + "_" + System.currentTimeMillis();
        
        // Send start marker
        String startPayload = "IMAGE_START:" + filename + ":" + id + ":" + totalChunks;
        sendRaw(startPayload);
        
        // Send chunks
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(base64Image.length(), start + chunkSize);
            String chunk = base64Image.substring(start, end);
            String chunkPayload = "IMAGE_CHUNK:" + id + ":" + i + ":" + chunk;
            sendRaw(chunkPayload);
            Thread.sleep(10); // Small delay between chunks
        }
        
        // Send end marker
        String endPayload = "IMAGE_END:" + id;
        sendRaw(endPayload);
        log.log("[MULTICAST] Sent chunked image: " + filename + " (" + imageBytes.length + " bytes, " + totalChunks + " chunks)");
    }

    private void sendRaw(String payload) throws Exception {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
            data,
            data.length,
            group,
            port
        );
        socket.send(packet);
    }

    /**
     * Check if the emitter is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Close the emitter.
     */
    public void close() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        log.log("[MULTICAST] Emitter closed");
    }
}
