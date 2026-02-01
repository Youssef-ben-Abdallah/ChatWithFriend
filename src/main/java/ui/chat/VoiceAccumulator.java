package ui.chat;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;

/** Collects streamed voice chunks into a single byte[] for replay. */
public final class VoiceAccumulator {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final AudioFormat format;

    public VoiceAccumulator(AudioFormat format) {
        this.format = format;
    }

    public void add(byte[] chunk) {
        try { out.write(chunk); } catch (Exception ignored) {}
    }

    public AudioFormat format() { return format; }

    public byte[] bytes() { return out.toByteArray(); }
}
