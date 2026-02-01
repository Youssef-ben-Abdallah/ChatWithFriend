package core.net;

import core.model.BinaryKind;

import javax.sound.sampled.AudioFormat;
import java.util.List;

/** UI implements this; networking cores call it. */
public interface ChatClientListener {
    void onUserList(List<String> users);

    void onText(String from, String to, String message);

    void onBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes);

    void onVoiceStart(String from, String to, AudioFormat format);

    void onVoiceChunk(String from, String to, byte[] pcmChunk);

    void onVoiceEnd(String from, String to);
}
