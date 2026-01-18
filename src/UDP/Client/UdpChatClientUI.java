package UDP.Client;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UdpChatClientUI extends JFrame {

    private static final String ALL = "All";
    private static final AudioFormat VOICE_FORMAT =
            new AudioFormat(16000f, 16, 1, true, false);

    private final String serverHost;
    private final int serverPort;
    private final String myName;

    private DatagramSocket socket;
    private InetAddress serverAddr;

    private volatile boolean connected = false;

    private final DefaultListModel<String> clientsModel = new DefaultListModel<>();
    private final JList<String> clientsList = new JList<>(clientsModel);

    private final JPanel chatPanel = new JPanel();
    private final JScrollPane chatScroll = new JScrollPane(chatPanel);

    private final JTextField messageField = new JTextField();

    // Buttons (need enable/disable)
    private JButton sendBtn;
    private JButton sendImgBtn;
    private JButton sendFileBtn;
    private JButton voiceBtn;
    private JButton disconnectBtn;
    private JButton reconnectBtn;

    // Voice recording
    private volatile boolean isRecording = false;
    private TargetDataLine micLine;
    private ByteArrayOutputStream recordBuffer;

    // Incoming stores
    private static class IncomingImage {
        String from; String fileName; int total;
        byte[][] parts; boolean[] got;
    }
    private final ConcurrentHashMap<String, IncomingImage> incomingImages = new ConcurrentHashMap<>();

    private static class IncomingFile {
        String from; String fileName; int total;
        byte[][] parts; boolean[] got; byte[] rebuiltBytes;
    }
    private final ConcurrentHashMap<String, IncomingFile> incomingFiles = new ConcurrentHashMap<>();

    private static class IncomingVoice {
        String from; int total;
        byte[][] parts; boolean[] got; byte[] rebuiltBytes;
    }
    private final ConcurrentHashMap<String, IncomingVoice> incomingVoices = new ConcurrentHashMap<>();

    public UdpChatClientUI(String serverHost, int serverPort, String myName) {
        super("UDP Chat - " + myName);
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.myName = myName;

        buildUI();
        connect(); // auto connect on start
    }

    private void buildUI() {
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        clientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clientsModel.addElement(ALL);
        clientsList.setSelectedIndex(0);

        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Left
        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Choose target"));
        leftPanel.add(new JScrollPane(clientsList), BorderLayout.CENTER);

        // Center
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Chat"));
        centerPanel.add(chatScroll, BorderLayout.CENTER);

        // Bottom buttons
        sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendMessage());

        sendImgBtn = new JButton("Send Image");
        sendImgBtn.addActionListener(e -> chooseAndSendImage());

        sendFileBtn = new JButton("Send File");
        sendFileBtn.addActionListener(e -> chooseAndSendFile());

        voiceBtn = new JButton("Record");
        voiceBtn.addActionListener(e -> toggleVoiceRecording());

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(messageField, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.add(voiceBtn);
        btns.add(sendFileBtn);
        btns.add(sendImgBtn);
        btns.add(sendBtn);

        bottom.add(btns, BorderLayout.EAST);
        centerPanel.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, centerPanel);
        split.setDividerLocation(260);

        // Top bar (connect controls)
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.addActionListener(e -> disconnect("Disconnected by user"));

        reconnectBtn = new JButton("Reconnect");
        reconnectBtn.addActionListener(e -> connect());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topBar.add(disconnectBtn);
        topBar.add(reconnectBtn);

        add(topBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        messageField.addActionListener(e -> sendMessage());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                try { if (isRecording) stopRecordingNoSend(); } catch (Exception ignored) {}
                disconnect("Window closed");
            }
        });

        setUiConnected(false);
    }

    // =========================
    // CONNECT / DISCONNECT
    // =========================
    private void connect() {
        if (connected) return;

        try {
            serverAddr = InetAddress.getByName(serverHost);
            socket = new DatagramSocket();
            connected = true;

            Thread receiver = new Thread(this::receiveLoop, "client-receiver");
            receiver.setDaemon(true);
            receiver.start();

            sendRaw("HELLO|" + myName + "|*|hi");
            appendText("Connected to server " + serverHost + ":" + serverPort);
            setUiConnected(true);

        } catch (Exception e) {
            connected = false;
            setUiConnected(false);
            JOptionPane.showMessageDialog(this, "Connect failed: " + e.getMessage());
        }
    }

    private void disconnect(String reason) {
        if (!connected) {
            setUiConnected(false);
            return;
        }

        try {
            // stop recording if needed
            if (isRecording) stopRecordingNoSend();

            sendRaw("LEAVE|" + myName + "|*|" + (reason == null ? "bye" : reason));
        } catch (Exception ignored) {}

        connected = false;
        setUiConnected(false);

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}

        appendText("Disconnected: " + (reason == null ? "" : reason));
    }

    private void setUiConnected(boolean on) {
        SwingUtilities.invokeLater(() -> {
            messageField.setEnabled(on);
            sendBtn.setEnabled(on);
            sendImgBtn.setEnabled(on);
            sendFileBtn.setEnabled(on);
            voiceBtn.setEnabled(on);
            disconnectBtn.setEnabled(on);
            reconnectBtn.setEnabled(!on);
        });
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507];

        while (connected && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleIncoming(msg);

            } catch (IOException e) {
                if (connected) appendText("Receive error: " + e.getMessage());
            }
        }
    }

    private void handleIncoming(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) return;

        String type = parts[0];
        String from = parts[1];
        String to = parts[2];
        String payload = parts[3];

        switch (type) {
            case "CLIENTS" -> SwingUtilities.invokeLater(() -> updateClientList(payload));

            case "MSG" -> {
                String line = "*".equals(to)
                        ? from + " (to all): " + payload
                        : from + " -> " + to + ": " + payload;
                appendText(line);
            }

            case "KICK" -> {
                // server instructs us to disconnect
                appendText("You were kicked by server. Reason: " + payload);
                disconnect("Kicked: " + payload);
            }

            case "IMG_START" -> handleImgStart(from, payload);
            case "IMG_CHUNK" -> handleImgChunk(payload);
            case "IMG_END" -> handleImgEnd(payload);

            case "FILE_START" -> handleFileStart(from, payload);
            case "FILE_CHUNK" -> handleFileChunk(payload);
            case "FILE_END" -> handleFileEnd(payload);

            case "VOICE_START" -> handleVoiceStart(from, payload);
            case "VOICE_CHUNK" -> handleVoiceChunk(payload);
            case "VOICE_END" -> handleVoiceEnd(payload);
        }
    }

    private void updateClientList(String csv) {
        String selected = clientsList.getSelectedValue();
        if (selected == null) selected = ALL;

        clientsModel.clear();
        clientsModel.addElement(ALL);

        if (csv != null && !csv.isBlank()) {
            java.util.List<String> names = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.equals(myName))
                    .toList();
            for (String n : names) clientsModel.addElement(n);
        }

        for (int i = 0; i < clientsModel.size(); i++) {
            if (clientsModel.get(i).equals(selected)) {
                clientsList.setSelectedIndex(i);
                return;
            }
        }
        clientsList.setSelectedIndex(0);
    }

    // =========================
    // SEND TEXT
    // =========================
    private void sendMessage() {
        if (!connected) return;

        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        String to = getSelectedTo();

        try {
            sendRaw("MSG|" + myName + "|" + to + "|" + text);
            appendText("Me -> " + readableTo(to) + ": " + text);
            messageField.setText("");
        } catch (Exception e) {
            appendText("Send error: " + e.getMessage());
        }
    }

    // =========================
    // IMAGE (same reliable settings)
    // =========================
    private void chooseAndSendImage() {
        if (!connected) return;
        String to = getSelectedTo();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose an image");
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif"));

        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        try {
            byte[] bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
            if (bytes.length > 300_000) {
                JOptionPane.showMessageDialog(this, "Image too large for UDP demo. Try < 300 KB.");
                return;
            }

            String fileName = chooser.getSelectedFile().getName();
            sendImageBytes(to, fileName, bytes);

            appendImage("Me -> " + readableTo(to) + " (image): " + fileName, new ImageIcon(bytes));
        } catch (Exception ex) {
            appendText("Image send error: " + ex.getMessage());
        }
    }

    private void sendImageBytes(String to, String fileName, byte[] bytes) throws IOException {
        String transferId = UUID.randomUUID().toString();

        int rawChunkSize = 400;
        int totalChunks = (int) Math.ceil(bytes.length / (double) rawChunkSize);

        for (int k = 0; k < 3; k++) {
            sendRaw("IMG_START|" + myName + "|" + to + "|" + transferId + ";" + fileName + ";" + totalChunks);
            sleepMs(10);
        }

        Base64.Encoder enc = Base64.getEncoder();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * rawChunkSize;
            int end = Math.min(bytes.length, start + rawChunkSize);
            byte[] part = Arrays.copyOfRange(bytes, start, end);

            String b64 = enc.encodeToString(part);
            sendRaw("IMG_CHUNK|" + myName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
            sleepMs(2);
        }

        for (int k = 0; k < 3; k++) {
            sendRaw("IMG_END|" + myName + "|" + to + "|" + transferId);
            sleepMs(10);
        }
    }

    private void handleImgStart(String from, String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        String fileName = p[1];

        int total;
        try { total = Integer.parseInt(p[2]); }
        catch (NumberFormatException e) { return; }

        IncomingImage img = new IncomingImage();
        img.from = from;
        img.fileName = fileName;
        img.total = total;
        img.parts = new byte[total][];
        img.got = new boolean[total];

        incomingImages.put(id, img);
        appendText("Incoming image from " + from + ": " + fileName);
    }

    private void handleImgChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        int idx;
        try { idx = Integer.parseInt(p[1]); }
        catch (NumberFormatException e) { return; }

        IncomingImage img = incomingImages.get(id);
        if (img == null || idx < 0 || idx >= img.total || img.got[idx]) return;

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            img.parts[idx] = part;
            img.got[idx] = true;
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleImgEnd(String payload) {
        String id = payload.trim();
        IncomingImage img = incomingImages.get(id);
        if (img == null) return;

        for (boolean b : img.got) {
            if (!b) {
                appendText("Image missing chunks (UDP loss). Ask sender to resend.");
                incomingImages.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < img.total; i++) full.write(img.parts[i]);
            byte[] bytes = full.toByteArray();

            appendImage(img.from + " (image): " + img.fileName, new ImageIcon(bytes));
        } catch (Exception e) {
            appendText("Failed to rebuild image: " + e.getMessage());
        } finally {
            incomingImages.remove(id);
        }
    }

    // =========================
    // FILE (same as before)
    // =========================
    private void chooseAndSendFile() {
        if (!connected) return;
        String to = getSelectedTo();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to send");

        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        try {
            File file = chooser.getSelectedFile();
            byte[] bytes = Files.readAllBytes(file.toPath());

            if (bytes.length > 800_000) {
                JOptionPane.showMessageDialog(this, "File too large for UDP demo. Try < 800 KB.");
                return;
            }

            sendFileBytes(to, file.getName(), bytes);
            appendText("Me -> " + readableTo(to) + " sent file: " + file.getName() + " (" + bytes.length + " bytes)");
        } catch (Exception e) {
            appendText("File send error: " + e.getMessage());
        }
    }

    private void sendFileBytes(String to, String fileName, byte[] bytes) throws IOException {
        String transferId = UUID.randomUUID().toString();

        int rawChunkSize = 400;
        int totalChunks = (int) Math.ceil(bytes.length / (double) rawChunkSize);

        for (int k = 0; k < 3; k++) {
            sendRaw("FILE_START|" + myName + "|" + to + "|" + transferId + ";" + fileName + ";" + totalChunks);
            sleepMs(10);
        }

        Base64.Encoder enc = Base64.getEncoder();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * rawChunkSize;
            int end = Math.min(bytes.length, start + rawChunkSize);
            byte[] part = Arrays.copyOfRange(bytes, start, end);

            String b64 = enc.encodeToString(part);
            sendRaw("FILE_CHUNK|" + myName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
            sleepMs(2);
        }

        for (int k = 0; k < 3; k++) {
            sendRaw("FILE_END|" + myName + "|" + to + "|" + transferId);
            sleepMs(10);
        }
    }

    private void handleFileStart(String from, String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        String fileName = p[1];
        int total;
        try { total = Integer.parseInt(p[2]); } catch (NumberFormatException e) { return; }

        IncomingFile f = new IncomingFile();
        f.from = from;
        f.fileName = fileName;
        f.total = total;
        f.parts = new byte[total][];
        f.got = new boolean[total];

        incomingFiles.put(id, f);
        appendText("Incoming file from " + from + ": " + fileName);
    }

    private void handleFileChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        int idx;
        try { idx = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }

        IncomingFile f = incomingFiles.get(id);
        if (f == null || idx < 0 || idx >= f.total || f.got[idx]) return;

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            f.parts[idx] = part;
            f.got[idx] = true;
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleFileEnd(String payload) {
        String id = payload.trim();
        IncomingFile f = incomingFiles.get(id);
        if (f == null) return;

        for (boolean b : f.got) {
            if (!b) {
                appendText("File missing chunks (UDP loss). Ask sender to resend.");
                incomingFiles.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < f.total; i++) full.write(f.parts[i]);
            f.rebuiltBytes = full.toByteArray();
            appendFileRow(f.from, f.fileName, f.rebuiltBytes);
        } catch (Exception e) {
            appendText("Failed to rebuild file: " + e.getMessage());
        } finally {
            incomingFiles.remove(id);
        }
    }

    private void appendFileRow(String from, String fileName, byte[] bytes) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel nameLabel = new JLabel("File from " + from + ": " + fileName + " (" + bytes.length + " bytes)");
            row.add(nameLabel, BorderLayout.CENTER);

            JButton viewBtn = new JButton("View");
            JButton downloadBtn = new JButton("Download");

            viewBtn.addActionListener(e -> {
                try {
                    File temp = File.createTempFile("udpchat_", "_" + sanitizeFileName(fileName));
                    Files.write(temp.toPath(), bytes);
                    temp.deleteOnExit();
                    Desktop.getDesktop().open(temp);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "View failed: " + ex.getMessage());
                }
            });

            downloadBtn.addActionListener(e -> {
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Save file as...");
                    chooser.setSelectedFile(new File(fileName));

                    int res = chooser.showSaveDialog(this);
                    if (res == JFileChooser.APPROVE_OPTION) {
                        File out = chooser.getSelectedFile();
                        Files.write(out.toPath(), bytes);
                        JOptionPane.showMessageDialog(this, "Saved: " + out.getAbsolutePath());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Download failed: " + ex.getMessage());
                }
            });

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            btns.add(viewBtn);
            btns.add(downloadBtn);

            row.add(btns, BorderLayout.EAST);

            chatPanel.add(row);
            chatPanel.add(Box.createVerticalStrut(10));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // =========================
    // VOICE (same behavior, now label changes on record)
    // =========================
    private void toggleVoiceRecording() {
        if (!connected) return;
        if (!isRecording) startRecording();
        else stopRecordingAndSend();
    }

    private void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, VOICE_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this, "Microphone line not supported on this device.");
                return;
            }

            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(VOICE_FORMAT);
            micLine.start();

            recordBuffer = new ByteArrayOutputStream();
            isRecording = true;
            voiceBtn.setText("Stop");

            appendText("Recording voice... Click Stop to send.");

            Thread t = new Thread(() -> {
                byte[] buffer = new byte[2048];
                while (isRecording && micLine != null && micLine.isOpen()) {
                    int count = micLine.read(buffer, 0, buffer.length);
                    if (count > 0) recordBuffer.write(buffer, 0, count);
                }
            }, "voice-recorder");
            t.setDaemon(true);
            t.start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Record failed: " + e.getMessage());
            isRecording = false;
            voiceBtn.setText("Record");
        }
    }

    private void stopRecordingNoSend() {
        isRecording = false;
        voiceBtn.setText("Record");
        if (micLine != null) {
            try { micLine.stop(); } catch (Exception ignored) {}
            try { micLine.close(); } catch (Exception ignored) {}
        }
        micLine = null;
        recordBuffer = null;
    }

    private void stopRecordingAndSend() {
        try {
            isRecording = false;
            voiceBtn.setText("Record");

            if (micLine != null) {
                micLine.stop();
                micLine.close();
            }

            byte[] pcm = (recordBuffer != null) ? recordBuffer.toByteArray() : new byte[0];

            if (pcm.length < 2000) {
                appendText("Voice note too short, not sent.");
                return;
            }
            if (pcm.length > 400_000) {
                appendText("Voice note too long for UDP demo. Keep it shorter.");
                return;
            }

            String to = getSelectedTo();
            sendVoiceBytes(to, pcm);

            appendVoiceRow("Me", pcm);

        } catch (Exception e) {
            appendText("Stop/send voice error: " + e.getMessage());
        } finally {
            micLine = null;
            recordBuffer = null;
        }
    }

    private void sendVoiceBytes(String to, byte[] pcmBytes) throws IOException {
        String transferId = UUID.randomUUID().toString();

        int rawChunkSize = 400;
        int totalChunks = (int) Math.ceil(pcmBytes.length / (double) rawChunkSize);

        for (int k = 0; k < 3; k++) {
            sendRaw("VOICE_START|" + myName + "|" + to + "|" + transferId + ";" + totalChunks);
            sleepMs(10);
        }

        Base64.Encoder enc = Base64.getEncoder();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * rawChunkSize;
            int end = Math.min(pcmBytes.length, start + rawChunkSize);
            byte[] part = Arrays.copyOfRange(pcmBytes, start, end);

            String b64 = enc.encodeToString(part);
            sendRaw("VOICE_CHUNK|" + myName + "|" + to + "|" + transferId + ";" + i + ";" + b64);
            sleepMs(2);
        }

        for (int k = 0; k < 3; k++) {
            sendRaw("VOICE_END|" + myName + "|" + to + "|" + transferId);
            sleepMs(10);
        }
    }

    private void handleVoiceStart(String from, String payload) {
        String[] p = payload.split(";", 2);
        if (p.length < 2) return;

        String id = p[0];
        int total;
        try { total = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }

        IncomingVoice v = new IncomingVoice();
        v.from = from;
        v.total = total;
        v.parts = new byte[total][];
        v.got = new boolean[total];

        incomingVoices.put(id, v);
        appendText("Incoming voice from " + from);
    }

    private void handleVoiceChunk(String payload) {
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        int idx;
        try { idx = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }

        IncomingVoice v = incomingVoices.get(id);
        if (v == null || idx < 0 || idx >= v.total || v.got[idx]) return;

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            v.parts[idx] = part;
            v.got[idx] = true;
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleVoiceEnd(String payload) {
        String id = payload.trim();
        IncomingVoice v = incomingVoices.get(id);
        if (v == null) return;

        for (boolean b : v.got) {
            if (!b) {
                appendText("Voice missing chunks (UDP loss). Ask sender to resend.");
                incomingVoices.remove(id);
                return;
            }
        }

        try {
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            for (int i = 0; i < v.total; i++) full.write(v.parts[i]);
            v.rebuiltBytes = full.toByteArray();

            appendVoiceRow(v.from, v.rebuiltBytes);

        } catch (Exception e) {
            appendText("Failed to rebuild voice: " + e.getMessage());
        } finally {
            incomingVoices.remove(id);
        }
    }

    private void appendVoiceRow(String from, byte[] pcmBytes) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            double seconds = pcmBytes.length / (VOICE_FORMAT.getSampleRate() * (VOICE_FORMAT.getSampleSizeInBits() / 8.0) * VOICE_FORMAT.getChannels());
            JLabel label = new JLabel("Voice from " + from + " (" + String.format("%.1f", seconds) + "s)");
            row.add(label, BorderLayout.CENTER);

            JButton playBtn = new JButton("Play");
            playBtn.addActionListener(e -> playPcmInApp(pcmBytes));
            row.add(playBtn, BorderLayout.EAST);

            chatPanel.add(row);
            chatPanel.add(Box.createVerticalStrut(10));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    private void playPcmInApp(byte[] pcmBytes) {
        new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, VOICE_FORMAT);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(VOICE_FORMAT);
                line.start();

                int offset = 0;
                int chunk = 2048;
                while (offset < pcmBytes.length) {
                    int len = Math.min(chunk, pcmBytes.length - offset);
                    line.write(pcmBytes, offset, len);
                    offset += len;
                }

                line.drain();
                line.stop();
                line.close();
            } catch (Exception ex) {
                appendText("Audio play error: " + ex.getMessage());
            }
        }, "voice-player").start();
    }

    // =========================
    // HELPERS / UI
    // =========================
    private String getSelectedTo() {
        String target = clientsList.getSelectedValue();
        if (target == null) target = ALL;
        return ALL.equals(target) ? "*" : target;
    }

    private String readableTo(String to) {
        return "*".equals(to) ? "All" : to;
    }

    private void sendRaw(String message) throws IOException {
        if (!connected || socket == null || socket.isClosed()) return;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddr, serverPort);
        socket.send(packet);
    }

    private void appendText(String line) {
        SwingUtilities.invokeLater(() -> {
            JLabel lbl = new JLabel("<html>" + escapeHtml(line) + "</html>");
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            chatPanel.add(lbl);
            chatPanel.add(Box.createVerticalStrut(6));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    private void appendImage(String caption, ImageIcon icon) {
        SwingUtilities.invokeLater(() -> {
            JLabel cap = new JLabel("<html><b>" + escapeHtml(caption) + "</b></html>");
            cap.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel img = new JLabel(scaleIcon(icon, 420));
            img.setAlignmentX(Component.LEFT_ALIGNMENT);

            chatPanel.add(cap);
            chatPanel.add(Box.createVerticalStrut(4));
            chatPanel.add(img);
            chatPanel.add(Box.createVerticalStrut(12));
            chatPanel.revalidate();
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ImageIcon scaleIcon(ImageIcon icon, int maxW) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) return icon;
        if (w <= maxW) return icon;

        int newH = (int) ((double) h * maxW / w);
        Image scaled = icon.getImage().getScaledInstance(maxW, newH, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static void sleepMs(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public static void main(String[] args) {
        String host = (args.length >= 1) ? args[0] : "127.0.0.1";
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 5000;

        String name = (args.length >= 3) ? args[2] : JOptionPane.showInputDialog("Enter your name:");
        if (name == null || name.isBlank()) return;

        SwingUtilities.invokeLater(() -> new UdpChatClientUI(host, port, name).setVisible(true));
    }
}
