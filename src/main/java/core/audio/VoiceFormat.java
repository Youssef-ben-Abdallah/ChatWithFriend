package core.audio;

import javax.sound.sampled.AudioFormat;

/** Shared PCM voice format for both TCP and UDP demos. */
public final class VoiceFormat {
    public static final float SAMPLE_RATE = 16_000f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;

    private VoiceFormat() {}

    public static AudioFormat pcm() {
        return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    }
}
