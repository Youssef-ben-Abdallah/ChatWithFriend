package ui;

import common.ChatClientInterface;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import javax.sound.sampled.*;

/**
 * Unified Chat Client UI that works with any ChatClientInterface
 * implementation.
 * Uses dependency injection to support both TCP and UDP protocols.
 */
public class UnifiedChatClientUI extends JFrame {
    private final ChatClientInterface client;
    private final String clientName;
    private final String serverAddress;
    private final int serverPort;

    // UI Components
    private JTextPane textPane;
    private StyledDocument doc;
    private JTextField textField;
    private JButton sendButton;
    private JButton attachButton;
    private JButton recordButton;
    private JButton disconnectButton;
    private JButton reconnectButton;
    private JList<String> userList;
    private DefaultListModel<String> listModel;

    // Audio recording
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private boolean isRecording = false;
    private ByteArrayOutputStream audioOutputStream;

    public UnifiedChatClientUI(ChatClientInterface client, String clientName, String serverAddress, int serverPort) {
        super("Chat Client - " + clientName);
        this.client = client;
        this.clientName = clientName;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        setupUI();
        setupClientListeners();

        // Auto-connect using provided parameters
        if (!client.isConnected()) {
            boolean ok = client.connect(serverAddress, serverPort, clientName);
            if (!ok) {
                appendText("Connection failed. Use Reconnect to try again.\n");
            }
        }
    }

    private void setupUI() {
        setSize(1100, 720);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // Chat area with styled text
        textPane = new JTextPane();
        textPane.setEditable(false);
        doc = textPane.getStyledDocument();
        JScrollPane centerScroll = new JScrollPane(textPane);

        // User list
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        listModel.addElement("All");
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(180, 0));

        // Bottom panel with input and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        textField = new JTextField();
        sendButton = new JButton("Send");
        attachButton = new JButton("Attach");
        recordButton = new JButton("Record");

        // Style record button
        recordButton.setBackground(new Color(220, 20, 60));
        recordButton.setForeground(Color.WHITE);
        recordButton.setFont(new Font("Arial", Font.BOLD, 11));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightButtons.add(recordButton);
        rightButtons.add(attachButton);
        rightButtons.add(sendButton);

        bottomPanel.add(textField, BorderLayout.CENTER);
        bottomPanel.add(rightButtons, BorderLayout.EAST);

        // Top bar with connection controls
        disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(e -> disconnect());

        reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener(e -> reconnect());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topBar.add(disconnectButton);
        topBar.add(reconnectButton);

        // Layout
        add(topBar, BorderLayout.NORTH);
        add(centerScroll, BorderLayout.CENTER);
        add(userScroll, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Event handlers
        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        textField.addActionListener(sendAction);

        attachButton.addActionListener(e -> chooseAndSendFile());
        recordButton.addActionListener(e -> toggleRecording());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isRecording) {
                    stopRecordingNoSend();
                }
                disconnect();
            }
        });

        updateConnectionUI();
    }

    private void setupClientListeners() {
        // Message received
        client.setOnMessageReceived((message, sender, isPrivate) -> {
            String prefix = isPrivate ? "(Private) " : "";
            appendText(prefix + sender + ": " + message + "\n");
        });

        // Image received
        client.setOnImageReceived((imageData, filename, sender, isPrivate) -> {
            displayImage(imageData, sender, filename, isPrivate);
        });

        // File received
        client.setOnFileReceived((fileData, filename, sender, isPrivate) -> {
            String fileType = filename.toLowerCase().endsWith(".pdf") ? "PDF" : "File";
            displayFile(fileData, sender, filename, fileType, isPrivate);
        });

        // Audio received
        client.setOnAudioReceived((audioData, filename, sender, isPrivate) -> {
            displayAudio(audioData, sender, filename, isPrivate);
        });

        // Client list updated
        client.setOnClientListUpdated(clients -> {
            SwingUtilities.invokeLater(() -> {
                String selected = userList.getSelectedValue();
                if (selected == null)
                    selected = "All";

                listModel.clear();
                listModel.addElement("All");
                for (String name : clients) {
                    if (!name.equals("All") && !name.equals(clientName)) {
                        listModel.addElement(name);
                    }
                }

                // Restore selection
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).equals(selected)) {
                        userList.setSelectedIndex(i);
                        return;
                    }
                }
                userList.setSelectedIndex(0);
            });
        });

        // Connection status changed
        client.setOnConnectionStatusChanged(connected -> {
            SwingUtilities.invokeLater(this::updateConnectionUI);
            if (connected) {
                appendText("Connected to server.\n");
            } else {
                appendText("Disconnected from server.\n");
            }
        });
    }

    private void updateConnectionUI() {
        boolean connected = client.isConnected();
        textField.setEnabled(connected);
        sendButton.setEnabled(connected);
        attachButton.setEnabled(connected);
        recordButton.setEnabled(connected);
        disconnectButton.setEnabled(connected);
        reconnectButton.setEnabled(!connected);
    }

    private void sendMessage() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected to server.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = textField.getText().trim();
        if (message.isEmpty())
            return;

        String target = userList.getSelectedValue();
        if (target == null)
            target = "All";

        client.sendMessage(message, target);
        textField.setText("");

        // Show sent message in UI
        String prefix = "All".equals(target) ? "" : "(to " + target + ") ";
        appendText("Me " + prefix + ": " + message + "\n");
    }

    private void chooseAndSendFile() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected to server.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
            return;

        File file = chooser.getSelectedFile();
        String target = userList.getSelectedValue();
        if (target == null)
            target = "All";

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String name = file.getName().toLowerCase();

            // Show sent file in UI
            String prefix = "All".equals(target) ? "" : "(to " + target + ") ";
            appendText("Me " + prefix + "sent: " + file.getName() + "\n");

            if (name.endsWith(".pdf")) {
                client.sendFile(data, file.getName(), target);
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".gif") ||
                    name.endsWith(".bmp")) {
                client.sendImage(data, file.getName(), target);
            } else if (name.endsWith(".wav") || name.endsWith(".mp3") ||
                    name.endsWith(".ogg")) {
                client.sendAudio(data, file.getName(), target);
            } else {
                client.sendFile(data, file.getName(), target);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to read file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleRecording() {
        if (!client.isConnected())
            return;
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 16000, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this, "Microphone not supported or not available",
                        "Recording Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            audioOutputStream = new ByteArrayOutputStream();
            isRecording = true;
            recordButton.setText("Stop");
            recordButton.setBackground(new Color(50, 205, 50));
            appendText("Recording... Click Stop to send.\n");

            new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (isRecording) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            audioOutputStream.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (LineUnavailableException ex) {
            JOptionPane.showMessageDialog(this, "Unable to access microphone: " + ex.getMessage(),
                    "Recording Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopRecording() {
        if (!isRecording || targetDataLine == null)
            return;

        isRecording = false;
        recordButton.setText("Record");
        recordButton.setBackground(new Color(220, 20, 60));

        try {
            targetDataLine.stop();
            targetDataLine.close();

            byte[] rawAudioData = audioOutputStream.toByteArray();
            audioOutputStream.close();

            if (rawAudioData.length == 0) {
                appendText("No audio recorded\n");
                return;
            }

            byte[] wavAudioData = convertToWav(rawAudioData);

            int choice = JOptionPane.showConfirmDialog(this,
                    "Recording complete (" + (wavAudioData.length / 1024) + " KB). Send voice message?",
                    "Send Voice Message", JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                String target = userList.getSelectedValue();
                if (target == null)
                    target = "All";

                client.sendAudio(wavAudioData, "voice_" + System.currentTimeMillis() + ".wav", target);
                appendText("Me sent voice message.\n");
            }

        } catch (Exception ex) {
            appendText("Error processing recording: " + ex.getMessage() + "\n");
        } finally {
            targetDataLine = null;
            audioOutputStream = null;
        }
    }

    private void stopRecordingNoSend() {
        isRecording = false;
        if (targetDataLine != null) {
            try {
                targetDataLine.stop();
                targetDataLine.close();
            } catch (Exception ignored) {
            }
        }
        targetDataLine = null;
        audioOutputStream = null;
    }

    private byte[] convertToWav(byte[] rawAudioData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write("RIFF".getBytes());
        writeInt(baos, 36 + rawAudioData.length);
        baos.write("WAVE".getBytes());
        baos.write("fmt ".getBytes());
        writeInt(baos, 16);
        writeShort(baos, 1);
        writeShort(baos, 1);
        writeInt(baos, 16000);
        writeInt(baos, 16000 * 2);
        writeShort(baos, 2);
        writeShort(baos, 16);
        baos.write("data".getBytes());
        writeInt(baos, rawAudioData.length);
        baos.write(rawAudioData);
        return baos.toByteArray();
    }

    private void writeInt(ByteArrayOutputStream baos, int value) throws IOException {
        baos.write(value & 0xFF);
        baos.write((value >> 8) & 0xFF);
        baos.write((value >> 16) & 0xFF);
        baos.write((value >> 24) & 0xFF);
    }

    private void writeShort(ByteArrayOutputStream baos, int value) throws IOException {
        baos.write(value & 0xFF);
        baos.write((value >> 8) & 0xFF);
    }

    private void disconnect() {
        if (client.isConnected()) {
            client.disconnect();
        }
    }

    private void reconnect() {
        if (client.isConnected()) {
            client.disconnect();
        }
        boolean ok = client.connect(serverAddress, serverPort, clientName);
        if (!ok) {
            appendText("Reconnect failed.\n");
        }
    }

    // Display methods (same as TCP client for rich display)
    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.insertString(doc.getLength(), text, null);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void displayImage(byte[] imgBytes, String sender, String filename, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            try {
                String header = (isPrivate ? "(Private Image from " : "Image from ") + sender + " — " + filename + "\n";
                doc.insertString(doc.getLength(), header, null);

                ImageIcon icon = new ImageIcon(imgBytes);
                if (icon.getIconWidth() > 400) {
                    Image img = icon.getImage();
                    double ratio = (double) icon.getIconHeight() / icon.getIconWidth();
                    Image scaled = img.getScaledInstance(400, (int) (400 * ratio), Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                }

                Style style = textPane.addStyle("img" + System.nanoTime(), null);
                StyleConstants.setIcon(style, icon);
                doc.insertString(doc.getLength(), "ignored", style);
                doc.insertString(doc.getLength(), " ", null);
                addDownloadButton(imgBytes, filename);
                doc.insertString(doc.getLength(), "\n\n", null);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        });
    }

    private void displayAudio(byte[] audioBytes, String sender, String filename, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            try {
                String header = (isPrivate ? "(Private Audio from " : "Voice message from ") + sender + ": " + filename
                        + " ";
                doc.insertString(doc.getLength(), header, null);

                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                JButton playBtn = new JButton("Play");
                playBtn.setMargin(new Insets(2, 8, 2, 8));
                playBtn.addActionListener(e -> playAudio(audioBytes, filename));
                buttonPanel.add(playBtn);

                JButton downloadBtn = new JButton("Save");
                downloadBtn.setMargin(new Insets(2, 8, 2, 8));
                downloadBtn.addActionListener(e -> downloadFile(audioBytes, filename));
                buttonPanel.add(downloadBtn);

                double duration = getAudioDuration(audioBytes, filename);
                String durationText = String.format("(%.1f sec)", duration);
                JLabel durationLabel = new JLabel(durationText);
                durationLabel.setFont(new Font("Arial", Font.PLAIN, 10));
                buttonPanel.add(durationLabel);

                textPane.setCaretPosition(doc.getLength());
                textPane.insertComponent(buttonPanel);
                doc.insertString(doc.getLength(), "\n\n", null);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void displayFile(byte[] fileBytes, String sender, String filename, String fileType, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            try {
                String icon = getFileIcon(filename);
                String header = (isPrivate ? "(Private " : "") + fileType + " from " + sender + ": " + filename + " ";
                doc.insertString(doc.getLength(), icon + " " + header, null);

                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                if (canOpenFile(filename)) {
                    JButton openBtn = new JButton("Open");
                    openBtn.setMargin(new Insets(2, 8, 2, 8));
                    openBtn.addActionListener(e -> openFile(fileBytes, filename));
                    buttonPanel.add(openBtn);
                }

                JButton downloadBtn = new JButton("Download");
                downloadBtn.setMargin(new Insets(2, 8, 2, 8));
                downloadBtn.addActionListener(e -> downloadFile(fileBytes, filename));
                buttonPanel.add(downloadBtn);

                double sizeInKB = fileBytes.length / 1024.0;
                String sizeText = String.format("(%.1f KB)", sizeInKB);
                JLabel sizeLabel = new JLabel(sizeText);
                sizeLabel.setFont(new Font("Arial", Font.PLAIN, 10));
                buttonPanel.add(sizeLabel);

                textPane.setCaretPosition(doc.getLength());
                textPane.insertComponent(buttonPanel);
                doc.insertString(doc.getLength(), "\n\n", null);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void addDownloadButton(byte[] data, String filename) {
        JButton downloadBtn = new JButton("Download");
        downloadBtn.setMargin(new Insets(2, 8, 2, 8));
        downloadBtn.setFont(new Font("Arial", Font.PLAIN, 10));
        downloadBtn.addActionListener(e -> downloadFile(data, filename));
        textPane.insertComponent(downloadBtn);
    }

    private boolean canOpenFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".txt") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".wav") ||
                lower.endsWith(".mp3");
    }

    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "[PDF]";
        if (lower.endsWith(".txt"))
            return "[TXT]";
        if (lower.endsWith(".doc") || lower.endsWith(".docx"))
            return "[DOC]";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx"))
            return "[XLS]";
        if (lower.endsWith(".zip") || lower.endsWith(".rar"))
            return "[ZIP]";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg"))
            return "[AUDIO]";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi"))
            return "[VIDEO]";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif"))
            return "[IMAGE]";
        return "[FILE]";
    }

    private void openFile(byte[] fileBytes, String filename) {
        try {
            File temp = File.createTempFile("chatfile_", "_" + filename);
            temp.deleteOnExit();
            Files.write(temp.toPath(), fileBytes);

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(temp);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Cannot open file automatically. File saved to: " + temp.getAbsolutePath(),
                            "File Saved", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadFile(byte[] fileBytes, String filename) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save File");
        fileChooser.setSelectedFile(new File(filename));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try {
                Files.write(fileToSave.toPath(), fileBytes);
                JOptionPane.showMessageDialog(this,
                        "File saved successfully to:\n" + fileToSave.getAbsolutePath(),
                        "Download Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void playAudio(byte[] audioBytes, String filename) {
        new Thread(() -> {
            try {
                File tempFile = File.createTempFile("playback_", filename);
                tempFile.deleteOnExit();
                Files.write(tempFile.toPath(), audioBytes);

                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(tempFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        tempFile.delete();
                        try {
                            audioInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                clip.start();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Unable to play audio: " + ex.getMessage(),
                            "Playback Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private double getAudioDuration(byte[] audioBytes, String filename) {
        try {
            if (filename.toLowerCase().endsWith(".wav") && audioBytes.length >= 44) {
                int sampleRate = ((audioBytes[24] & 0xFF) |
                        ((audioBytes[25] & 0xFF) << 8) |
                        ((audioBytes[26] & 0xFF) << 16) |
                        ((audioBytes[27] & 0xFF) << 24));
                int dataSize = ((audioBytes[40] & 0xFF) |
                        ((audioBytes[41] & 0xFF) << 8) |
                        ((audioBytes[42] & 0xFF) << 16) |
                        ((audioBytes[43] & 0xFF) << 24));
                if (sampleRate > 0) {
                    return dataSize / (sampleRate * 2.0);
                }
            }
        } catch (Exception e) {
        }
        return audioBytes.length / 32000.0;
    }
}
