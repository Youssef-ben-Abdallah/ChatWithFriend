package core.audio;

import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures microphone PCM data in a background thread and feeds chunks to a callback.
 * Designed for "push-to-talk" style streaming.
 */
public final class AudioCapture implements AutoCloseable {

    public interface ChunkHandler {
        void onChunk(byte[] data, int len) throws Exception;
    }

    public interface ErrorHandler {
        void onError(Exception e);
    }

    private final AudioFormat format;
    private TargetDataLine line;
    private Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AudioCapture(AudioFormat format) {
        this.format = format;
    }

    public void start(int chunkSizeBytes, ChunkHandler handler, ErrorHandler errorHandler) throws LineUnavailableException {
        if (running.get()) return;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        running.set(true);
        thread = new Thread(() -> {
            byte[] buf = new byte[Math.max(256, chunkSizeBytes)];
            try {
                while (running.get()) {
                    int n = line.read(buf, 0, buf.length);
                    if (n > 0) handler.onChunk(buf, n);
                }
            } catch (Exception e) {
                if (errorHandler != null) errorHandler.onError(e);
            }
        }, "AudioCapture");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
        }
        line = null;
    }

    @Override public void close() { stop(); }
}
