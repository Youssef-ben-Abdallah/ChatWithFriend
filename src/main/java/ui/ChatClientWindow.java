package ui;

import core.audio.AudioCapture;
import core.audio.PcmPlayer;
import core.audio.VoiceFormat;
import core.model.BinaryKind;
import core.net.ChatClientApi;
import core.net.ChatClientListener;
import core.util.SwingUtil;
import ui.chat.ChatPane;
import ui.chat.VoiceAccumulator;
import ui.icons.Icons;

import javax.sound.sampled.AudioFormat;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic client window reused for TCP and UDP.
 *
 * New UX:
 * - Record voice locally (mic->stop)
 * - Preview (play)
 * - Send voice (send button when no text) OR send text (when text exists)
 */
public final class ChatClientWindow extends JFrame implements ChatClientListener {

    private ChatClientApi client; // attached later
    private final ChatPane chat = new ChatPane();

    private static final String ALL = "All";

    private final JComboBox<String> toBox = new JComboBox<>(new String[]{ALL});
    private final JTextField input = new JTextField();

    private final JButton btnImage = Theme.squareIconButton(Icons.image(18), "Send image");
    private final JButton btnFile  = Theme.squareIconButton(Icons.file(18), "Send file");
    private final JButton btnMic   = Theme.squareIconButton(Icons.mic(18), "Record voice");
    private final JButton btnPlay  = Theme.squareIconButton(Icons.play(18), "Play recorded voice");
    private final JButton btnSend  = Theme.squareIconButton(Icons.send(18), "Send");

    // voice receive accumulators keyed by from+to
    private final Map<String, VoiceAccumulator> voices = new ConcurrentHashMap<>();

    // voice recording (local preview)
    private volatile boolean recording = false;
    private AudioCapture capture;
    private final AudioFormat voiceFmt = VoiceFormat.pcm();
    private ByteArrayOutputStream recorded;
    private byte[] recordedBytes;

    private String myName = "me";

    public ChatClientWindow(String title) {
        super(title);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(740, 580);
        setLocationRelativeTo(null);

        // Top: "To" selector
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        top.add(new JLabel("To:"), BorderLayout.WEST);
        top.add(toBox, BorderLayout.CENTER);

        // Bottom: input + square buttons in one row (tight)
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(btnImage);
        actions.add(btnFile);
        actions.add(btnMic);
        actions.add(btnPlay);
        actions.add(btnSend);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(chat, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Initial states
        btnPlay.setEnabled(false);

        // Actions
        btnSend.addActionListener(e -> onSend());
        input.addActionListener(e -> onSend());

        btnImage.addActionListener(e -> onSendBinary(BinaryKind.IMAGE));
        btnFile.addActionListener(e -> onSendBinary(BinaryKind.FILE));

        btnMic.addActionListener(e -> onMicToggle());
        btnPlay.addActionListener(e -> onPlayPreview());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                try { stopRecordingIfNeeded(); } catch (Exception ignored) {}
                try { if (client != null) client.close(); } catch (Exception ignored) {}
            }
        });
    }

    /** Attach a client core AFTER creating the window, so the window can be the listener. */
    public void attachClient(ChatClientApi client) {
        this.client = client;
        this.myName = client.name();
    }

    public void connectAndShow() {
        try {
            if (client == null) throw new IllegalStateException("Client not attached");
            client.connect();
            setVisible(true);
            chat.addText("Connected as " + myName);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connect failed: " + e.getMessage());
            try { if (client != null) client.close(); } catch (Exception ignored) {}
            dispose();
        }
    }

    private String selectedTo() {
        Object v = toBox.getSelectedItem();
        String s = (v == null) ? ALL : v.toString();
        return ALL.equals(s) ? "*" : s;
    }

    /**
     * Send button behavior:
     * - If text input is not empty -> send text
     * - Else if there is recorded voice pending -> send voice
     */
    private void onSend() {
        try { ensure(); } catch (Exception e) { chat.addText(e.getMessage()); return; }

        String to = selectedTo();
        String msg = input.getText().trim();

        if (!msg.isEmpty()) {
            try {
                client.sendText(to, msg);
                chat.addText("Me -> " + readableTo(to) + ": " + msg);
                input.setText("");
            } catch (Exception e) {
                chat.addText("Send error: " + e.getMessage());
            }
            return;
        }

        if (recordedBytes != null && recordedBytes.length > 0) {
            try {
                client.sendVoice(to, voiceFmt, recordedBytes);
                chat.addText("Me -> " + readableTo(to) + ": [voice]");
                clearRecorded();
            } catch (Exception e) {
                chat.addText("Voice send error: " + e.getMessage());
            }
        }
    }

    private void onSendBinary(BinaryKind kind) {
        try { ensure(); } catch (Exception e) { chat.addText(e.getMessage()); return; }
        String to = selectedTo();

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(kind == BinaryKind.IMAGE ? "Choose Image" : "Choose File");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        try {
            client.sendBinary(kind, to, f);
            chat.addText("Me -> " + readableTo(to) + " sent " + kind + ": " + f.getName());
        } catch (Exception e) {
            chat.addText("Send error: " + e.getMessage());
        }
    }

    private void onMicToggle() {
        try { ensure(); } catch (Exception e) { chat.addText(e.getMessage()); return; }

        if (!recording) startRecording();
        else stopRecording();
    }

    private void startRecording() {
        try {
            stopRecordingIfNeeded();
            recorded = new ByteArrayOutputStream();
            recordedBytes = null;
            btnPlay.setEnabled(false);

            capture = new AudioCapture(voiceFmt);
            recording = true;

            // UI: mic becomes stop icon
            btnMic.setIcon(Icons.stop(18));
            btnMic.setToolTipText("Stop recording");

            chat.addText("Recording voice... (press stop)");

            capture.start(1024, (buf, len) -> {
                if (!recording) return;
                recorded.write(buf, 0, len);
            }, e -> chat.addText("Capture error: " + e.getMessage()));

        } catch (Exception e) {
            recording = false;
            btnMic.setIcon(Icons.mic(18));
            btnMic.setToolTipText("Record voice");
            chat.addText("Voice record failed: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            recording = false;
            if (capture != null) capture.stop();
            capture = null;

            recordedBytes = (recorded != null) ? recorded.toByteArray() : null;
            recorded = null;

            // UI: back to mic icon
            btnMic.setIcon(Icons.mic(18));
            btnMic.setToolTipText("Record voice");

            if (recordedBytes != null && recordedBytes.length > 0) {
                btnPlay.setEnabled(true);
                chat.addText("Voice recorded. You can preview (play) and press send to deliver it.");
            } else {
                btnPlay.setEnabled(false);
                chat.addText("No voice captured.");
            }

        } catch (Exception e) {
            chat.addText("Stop record failed: " + e.getMessage());
        }
    }

    private void stopRecordingIfNeeded() {
        if (recording) stopRecording();
    }

    private void onPlayPreview() {
        if (recordedBytes == null || recordedBytes.length == 0) return;

        new Thread(() -> {
            try {
                PcmPlayer.play(voiceFmt, recordedBytes);
            } catch (Exception e) {
                SwingUtil.ui(() -> JOptionPane.showMessageDialog(this, "Play failed: " + e.getMessage()));
            }
        }, "VoicePreview").start();
    }

    private void clearRecorded() {
        recordedBytes = null;
        btnPlay.setEnabled(false);
    }

    private void ensure() {
        if (client == null) throw new IllegalStateException("Client not attached");
        if (!client.isConnected()) throw new IllegalStateException("Not connected");
    }

    // ===== Server monitor helpers =====

    public void addServerNote(String message) {
        chat.addText("[Server] " + message);
    }

    public void addServerText(String line) {
        chat.addText("[Server] " + line);
    }

    public void addServerBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes) {
        String label = "[Server] " + from + " -> " + readableTo(to);
        if (kind == BinaryKind.IMAGE) {
            chat.addImage(label + " (image): " + fileName, new ImageIcon(bytes));
        } else {
            chat.addFileAttachment(label + " (file):", fileName, bytes);
        }
    }

    public void addServerVoiceStart(String from, String to, AudioFormat format) {
        String key = "server:" + from + "->" + to;
        voices.put(key, new VoiceAccumulator(format));
        chat.addText("[Server] Incoming voice from " + from + "...");
    }

    public void addServerVoiceChunk(String from, String to, byte[] pcmChunk) {
        String key = "server:" + from + "->" + to;
        VoiceAccumulator acc = voices.computeIfAbsent(key, k -> new VoiceAccumulator(VoiceFormat.pcm()));
        acc.add(pcmChunk);
    }

    public void addServerVoiceEnd(String from, String to) {
        String key = "server:" + from + "->" + to;
        VoiceAccumulator acc = voices.remove(key);
        if (acc == null) return;
        chat.addVoice("[Server] " + from + " (voice):", acc.format(), acc.bytes());
    }

    // ===== Listener callbacks from cores =====

    @Override public void onUserList(List<String> users) {
        SwingUtil.ui(() -> {
            String current = selectedTo();
            Set<String> unique = new LinkedHashSet<>();
            unique.add(ALL);
            for (String u : users) {
                if (u == null) continue;
                u = u.trim();
                if (!u.isEmpty() && !u.equals(myName)) unique.add(u);
            }
            toBox.setModel(new DefaultComboBoxModel<>(unique.toArray(new String[0])));
            toBox.setSelectedItem(current);
        });
    }

    @Override public void onText(String from, String to, String message) {
        chat.addText(from + " -> " + readableTo(to) + ": " + message);
    }

    @Override public void onBinary(BinaryKind kind, String from, String to, String fileName, byte[] bytes) {
        if (kind == BinaryKind.IMAGE) chat.addImage(from + " (image): " + fileName, new ImageIcon(bytes));
        else chat.addFileAttachment(from + " (file):", fileName, bytes);
    }

    @Override public void onVoiceStart(String from, String to, AudioFormat format) {
        String key = from + "->" + to;
        voices.put(key, new VoiceAccumulator(format));
        chat.addText("Incoming voice from " + from + "...");
    }

    @Override public void onVoiceChunk(String from, String to, byte[] pcmChunk) {
        String key = from + "->" + to;
        VoiceAccumulator acc = voices.computeIfAbsent(key, k -> new VoiceAccumulator(VoiceFormat.pcm()));
        acc.add(pcmChunk);
    }

    @Override public void onVoiceEnd(String from, String to) {
        String key = from + "->" + to;
        VoiceAccumulator acc = voices.remove(key);
        if (acc == null) return;
        chat.addVoice(from + " (voice):", acc.format(), acc.bytes());
    }

    private static String readableTo(String to) {
        return "*".equals(to) ? ALL : to;
    }
}
