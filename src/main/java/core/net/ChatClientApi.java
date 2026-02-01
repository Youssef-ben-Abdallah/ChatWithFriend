package core.net;

import core.model.BinaryKind;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;

/** UI talks to a client core through this interface (TCP and UDP implement it). */
public interface ChatClientApi extends AutoCloseable {
    void connect() throws IOException;
    boolean isConnected();

    String name();

    void sendText(String to, String message) throws IOException;

    void sendBinary(BinaryKind kind, String to, File file) throws IOException;

    /**
     * Send a complete voice message (PCM bytes). This supports "record -> preview -> send".
     * The UI can still simulate "live" by calling this repeatedly, but default UX is buffered.
     */
    void sendVoice(String to, AudioFormat format, byte[] pcmBytes) throws IOException;

    @Override void close();
}
