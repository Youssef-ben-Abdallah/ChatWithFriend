package core.audio;

import javax.sound.sampled.*;

/** Plays PCM bytes (blocking). */
public final class PcmPlayer {
    private PcmPlayer() {}

    public static void play(AudioFormat format, byte[] pcm) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        }
    }
}
