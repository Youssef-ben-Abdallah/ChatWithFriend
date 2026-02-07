package multicast;

import core.net.LogSink;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multicast receiver.
 * Receives messages from a multicast group.
 */
public final class MulticastRecepteur {
    private final String multicastAddress;
    private final int port;
    private final String name;
    private final LogSink log;

    private MulticastSocket socket;
    private InetAddress group;
    private Thread rxThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile MulticastMessageListener messageListener;
    private final MulticastImageReassembler imageReassembler = new MulticastImageReassembler();

    public MulticastRecepteur(String multicastAddress, int port, String name, LogSink log) {
        this.multicastAddress = multicastAddress;
        this.port = port;
        this.name = name;
        this.log = (log == null) ? LogSink.stdout() : log;
    }

    public void setMessageListener(MulticastMessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Start the receiver.
     */
    public void start() throws Exception {
        if (running.get()) return;

        group = InetAddress.getByName(multicastAddress);
        socket = new MulticastSocket(port);
        socket.joinGroup(group);

        running.set(true);
        rxThread = new Thread(this::rxLoop, "MulticastRecepteur-Rx-" + name);
        rxThread.setDaemon(true);
        rxThread.start();
        log.log("[MULTICAST] Receiver (" + name + ") started on " + multicastAddress + ":" + port);
    }

    private void rxLoop() {
        byte[] buf = new byte[65_000];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String message = new String(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength(),
                    StandardCharsets.UTF_8
                );
                
                MulticastMessageListener listener = messageListener;
                
                // Handle different message types
                if (message.startsWith("TEXT:")) {
                    String text = message.substring(5);
                    log.log("[MULTICAST] Receiver (" + name + ") received text: " + text);
                    if (listener != null) {
                        try {
                            listener.onMessage(text);
                        } catch (Exception e) {
                            log.log("[MULTICAST] Listener error: " + e.getMessage());
                        }
                    }
                } else if (message.startsWith("IMAGE:")) {
                    // Single packet image
                    String[] parts = message.substring(6).split(":", 2);
                    if (parts.length == 2) {
                        String filename = parts[0];
                        String base64Image = parts[1];
                        try {
                            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                            log.log("[MULTICAST] Receiver (" + name + ") received image: " + filename);
                            if (listener != null) {
                                try {
                                    listener.onImage(filename, imageBytes);
                                } catch (Exception e) {
                                    log.log("[MULTICAST] Image listener error: " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.log("[MULTICAST] Image decode error: " + e.getMessage());
                        }
                    }
                } else if (message.startsWith("IMAGE_START:")) {
                    // Chunked image start
                    String[] parts = message.substring(12).split(":", 3);
                    if (parts.length == 3) {
                        String filename = parts[0];
                        String id = parts[1];
                        int totalChunks = Integer.parseInt(parts[2]);
                        imageReassembler.onImageStart(filename, id, totalChunks);
                        log.log("[MULTICAST] Receiver (" + name + ") receiving chunked image: " + filename);
                    }
                } else if (message.startsWith("IMAGE_CHUNK:")) {
                    // Chunked image chunk
                    String[] parts = message.substring(12).split(":", 3);
                    if (parts.length == 3) {
                        String id = parts[0];
                        int chunkIndex = Integer.parseInt(parts[1]);
                        String chunk = parts[2];
                        imageReassembler.onImageChunk(id, chunkIndex, chunk);
                    }
                } else if (message.startsWith("IMAGE_END:")) {
                    // Chunked image end
                    String id = message.substring(10);
                    MulticastImageReassembler.ImageResult result = imageReassembler.onImageEnd(id);
                    if (result != null) {
                        log.log("[MULTICAST] Receiver (" + name + ") received chunked image: " + result.filename);
                        if (listener != null) {
                            try {
                                listener.onImage(result.filename, result.imageData);
                            } catch (Exception e) {
                                log.log("[MULTICAST] Image listener error: " + e.getMessage());
                            }
                        }
                    } else {
                        log.log("[MULTICAST] Receiver (" + name + ") failed to reassemble image");
                    }
                } else {
                    // Legacy: plain text (for backward compatibility)
                    log.log("[MULTICAST] Receiver (" + name + ") received: " + message);
                    if (listener != null) {
                        try {
                            listener.onMessage(message);
                        } catch (Exception e) {
                            log.log("[MULTICAST] Listener error: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.log("[MULTICAST] Receiver (" + name + ") error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Check if the receiver is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Close the receiver.
     */
    public void close() {
        running.set(false);
        if (socket != null) {
            try {
                if (group != null) {
                    socket.leaveGroup(group);
                }
            } catch (Exception ignored) {}
            socket.close();
            socket = null;
        }
        log.log("[MULTICAST] Receiver (" + name + ") closed");
    }
}
