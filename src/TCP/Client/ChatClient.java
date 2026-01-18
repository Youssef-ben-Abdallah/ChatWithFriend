package TCP.Client;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.*;
import java.util.Objects;

public class ChatClient extends JFrame {
    private JTextPane textPane;
    private StyledDocument doc;
    private JTextField textField;
    private JButton sendButton;
    private JButton attachButton;
    private JButton recordButton;
    private DataOutputStream out;
    private DataInputStream in;
    private String clientName;
    private JList<String> userList;
    private DefaultListModel<String> listModel;
    private Socket socket;

    // Audio recording variables
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private boolean isRecording = false;
    private ByteArrayOutputStream audioOutputStream;

    public ChatClient(String serverAddress, int port) {
        // Ask name
        clientName = JOptionPane.showInputDialog(null, "Enter your name:");
        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Anonymous";
        }

        setTitle("Chat Client - " + clientName);
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        textPane = new JTextPane();
        textPane.setEditable(false);
        doc = textPane.getStyledDocument();
        JScrollPane centerScroll = new JScrollPane(textPane);

        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        listModel.addElement("All");
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(180, 0));

        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        textField = new JTextField();
        sendButton = new JButton("Send");
        attachButton = new JButton("Attach");
        recordButton = new JButton("🎤 Record");

        // Style the record button differently
        recordButton.setBackground(new Color(220, 20, 60)); // Crimson red
        recordButton.setForeground(Color.WHITE);
        recordButton.setFont(new Font("Arial", Font.BOLD, 11));

        JPanel rightButtons = new JPanel(new GridLayout(1, 3, 6, 6));
        rightButtons.add(recordButton);
        rightButtons.add(attachButton);
        rightButtons.add(sendButton);

        bottomPanel.add(textField, BorderLayout.CENTER);
        bottomPanel.add(rightButtons, BorderLayout.EAST);

        add(centerScroll, BorderLayout.CENTER);
        add(userScroll, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        ActionListener sendAction = e -> sendTextMessage();
        sendButton.addActionListener(sendAction);
        textField.addActionListener(sendAction);

        attachButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                String name = file.getName().toLowerCase();
                if (name.endsWith(".pdf")) {
                    sendPdfFile(file);
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                        name.endsWith(".png") || name.endsWith(".gif") ||
                        name.endsWith(".bmp")) {
                    sendImageFile(file);
                } else if (name.endsWith(".wav") || name.endsWith(".mp3") ||
                        name.endsWith(".ogg")) {
                    sendAudioFile(file);
                } else {
                    sendGenericFile(file);
                }
            }
        });

        // Voice recording button
        recordButton.addActionListener(e -> toggleRecording());

        setVisible(true);

        try {
            socket = new Socket(serverAddress, port);
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeUTF("NAME:" + clientName);
            out.flush();
            new Thread(this::listenLoop).start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server: " + ex.getMessage(),
                    "Connection error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            dispose();
        }
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            // Setup audio format - using a standard format for better compatibility
            audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 16000, false);

            // Get target data line
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                JOptionPane.showMessageDialog(this,
                        "Microphone not supported or not available",
                        "Recording Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            audioOutputStream = new ByteArrayOutputStream();

            // Start recording in a separate thread
            isRecording = true;
            recordButton.setText("⏹️ Stop");
            recordButton.setBackground(new Color(50, 205, 50)); // Lime green when recording
            appendText("🎤 Recording started... Speak now!\n");

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
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Unable to access microphone: " + ex.getMessage(),
                    "Recording Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopRecording() {
        if (!isRecording || targetDataLine == null) {
            return;
        }

        isRecording = false;
        recordButton.setText("🎤 Record");
        recordButton.setBackground(new Color(220, 20, 60)); // Back to red

        try {
            targetDataLine.stop();
            targetDataLine.close();

            byte[] rawAudioData = audioOutputStream.toByteArray();
            audioOutputStream.close();

            if (rawAudioData.length == 0) {
                appendText("🎤 No audio recorded\n");
                return;
            }

            // Convert to proper WAV format
            byte[] wavAudioData = convertToWav(rawAudioData);

            // Ask if user wants to send the recording
            int choice = JOptionPane.showConfirmDialog(this,
                    "Recording complete (" + (wavAudioData.length / 1024) + " KB). Send voice message?",
                    "Send Voice Message",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                // Create a temporary WAV file
                File tempFile = File.createTempFile("voice_", ".wav");
                tempFile.deleteOnExit();

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(wavAudioData);
                }

                sendAudioFile(tempFile);
                tempFile.delete(); // Clean up temp file
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            appendText("⚠️ Error processing recording: " + ex.getMessage() + "\n");
        }
    }

    private byte[] convertToWav(byte[] rawAudioData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // WAV header (44 bytes)
        // RIFF header
        baos.write("RIFF".getBytes()); // ChunkID
        writeInt(baos, 36 + rawAudioData.length); // ChunkSize
        baos.write("WAVE".getBytes()); // Format

        // fmt subchunk
        baos.write("fmt ".getBytes()); // Subchunk1ID
        writeInt(baos, 16); // Subchunk1Size
        writeShort(baos, 1); // AudioFormat (1 = PCM)
        writeShort(baos, 1); // NumChannels (1 = mono)
        writeInt(baos, 16000); // SampleRate
        writeInt(baos, 16000 * 2); // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
        writeShort(baos, 2); // BlockAlign (NumChannels * BitsPerSample/8)
        writeShort(baos, 16); // BitsPerSample

        // data subchunk
        baos.write("data".getBytes()); // Subchunk2ID
        writeInt(baos, rawAudioData.length); // Subchunk2Size
        baos.write(rawAudioData); // Audio data

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

    private void listenLoop() {
        try {
            while (true) {
                String header = in.readUTF();
                if (header.startsWith("USER_LIST:")) {
                    updateUserList(header.substring("USER_LIST:".length()));
                } else if (header.startsWith("IMG_ALL:") || header.startsWith("IMG_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (header.startsWith("IMG_ALL:") && parts.length >= 4) {
                        String sender = parts[1];
                        String filename = parts[2];
                        int size = Integer.parseInt(parts[3]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        displayImage(data, sender, filename, false);
                    } else if (header.startsWith("IMG_TO:") && parts.length >= 5) {
                        String target = parts[1];
                        String sender = parts[2];
                        String filename = parts[3];
                        int size = Integer.parseInt(parts[4]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        if (Objects.equals(target, clientName) || Objects.equals(target, "All")) {
                            displayImage(data, sender, filename, true);
                        }
                    }
                } else if (header.startsWith("PDF_ALL:") || header.startsWith("PDF_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (header.startsWith("PDF_ALL:") && parts.length >= 4) {
                        String sender = parts[1];
                        String filename = parts[2];
                        int size = Integer.parseInt(parts[3]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        displayFile(data, sender, filename, "PDF", false);
                    } else if (header.startsWith("PDF_TO:") && parts.length >= 5) {
                        String target = parts[1];
                        String sender = parts[2];
                        String filename = parts[3];
                        int size = Integer.parseInt(parts[4]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        if (Objects.equals(target, clientName) || Objects.equals(target, "All")) {
                            displayFile(data, sender, filename, "PDF", true);
                        }
                    }
                } else if (header.startsWith("AUDIO_ALL:") || header.startsWith("AUDIO_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (header.startsWith("AUDIO_ALL:") && parts.length >= 4) {
                        String sender = parts[1];
                        String filename = parts[2];
                        int size = Integer.parseInt(parts[3]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        displayAudio(data, sender, filename, false);
                    } else if (header.startsWith("AUDIO_TO:") && parts.length >= 5) {
                        String target = parts[1];
                        String sender = parts[2];
                        String filename = parts[3];
                        int size = Integer.parseInt(parts[4]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        if (Objects.equals(target, clientName) || Objects.equals(target, "All")) {
                            displayAudio(data, sender, filename, true);
                        }
                    }
                } else if (header.startsWith("FILE_ALL:") || header.startsWith("FILE_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (header.startsWith("FILE_ALL:") && parts.length >= 4) {
                        String sender = parts[1];
                        String filename = parts[2];
                        int size = Integer.parseInt(parts[3]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        displayFile(data, sender, filename, "File", false);
                    } else if (header.startsWith("FILE_TO:") && parts.length >= 5) {
                        String target = parts[1];
                        String sender = parts[2];
                        String filename = parts[3];
                        int size = Integer.parseInt(parts[4]);
                        byte[] data = new byte[size];
                        in.readFully(data);
                        if (Objects.equals(target, clientName) || Objects.equals(target, "All")) {
                            displayFile(data, sender, filename, "File", true);
                        }
                    }
                } else {
                    appendText(header + "\n");
                }
            }
        } catch (EOFException eof) {
            appendText("\n-- Disconnected from server --\n");
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("\n-- Connection error: " + ex.getMessage() + " --\n");
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void updateUserList(String usersCsv) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            listModel.addElement("All");
            if (usersCsv.trim().isEmpty()) return;
            for (String u : usersCsv.split(",")) {
                u = u.trim();
                if (!u.isEmpty() && !u.equals(clientName)) listModel.addElement(u);
            }
        });
    }

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

                // Add download button for images too
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
                String header = (isPrivate ? "(Private Audio from " : "🎤 Voice message from ") + sender + ": " + filename + " ";
                doc.insertString(doc.getLength(), header, null);

                // Create button panel for audio
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

                // Play button
                JButton playBtn = new JButton("▶️ Play");
                playBtn.setMargin(new Insets(2, 8, 2, 8));
                playBtn.addActionListener(e -> playAudio(audioBytes, filename));
                buttonPanel.add(playBtn);

                // Download button
                JButton downloadBtn = new JButton("💾 Save");
                downloadBtn.setMargin(new Insets(2, 8, 2, 8));
                downloadBtn.addActionListener(e -> downloadFile(audioBytes, filename));
                buttonPanel.add(downloadBtn);

                // Add duration info (estimate)
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

    private double getAudioDuration(byte[] audioBytes, String filename) {
        try {
            if (filename.toLowerCase().endsWith(".wav")) {
                // For WAV files, calculate duration from header
                if (audioBytes.length >= 44) {
                    // Bytes 24-27 contain sample rate
                    int sampleRate = ((audioBytes[24] & 0xFF) |
                            ((audioBytes[25] & 0xFF) << 8) |
                            ((audioBytes[26] & 0xFF) << 16) |
                            ((audioBytes[27] & 0xFF) << 24));

                    // Bytes 40-43 contain data size
                    int dataSize = ((audioBytes[40] & 0xFF) |
                            ((audioBytes[41] & 0xFF) << 8) |
                            ((audioBytes[42] & 0xFF) << 16) |
                            ((audioBytes[43] & 0xFF) << 24));

                    if (sampleRate > 0) {
                        // Calculate duration: data size / (sample rate * channels * bits per sample / 8)
                        // Assuming 16-bit mono for voice recordings
                        return dataSize / (sampleRate * 2.0);
                    }
                }
            }
        } catch (Exception e) {
            // If we can't parse the header, fall back to estimation
        }

        // Fallback estimation for non-WAV or corrupted files
        return audioBytes.length / 32000.0; // Rough estimate
    }

    private void playAudio(byte[] audioBytes, String filename) {
        new Thread(() -> {
            try {
                // Create a temporary file for playback
                File tempFile = File.createTempFile("playback_", filename);
                tempFile.deleteOnExit();

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(audioBytes);
                }

                // Play the audio file
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(tempFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);

                // Add a listener to clean up when done
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

            } catch (UnsupportedAudioFileException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Unsupported audio format. Try saving and playing with another application.",
                            "Playback Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            } catch (LineUnavailableException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Audio device is busy or unavailable.",
                            "Playback Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Unable to play audio: " + ex.getMessage(),
                            "Playback Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void displayFile(byte[] fileBytes, String sender, String filename, String fileType, boolean isPrivate) {
        SwingUtilities.invokeLater(() -> {
            try {
                String icon = getFileIcon(filename);
                String header = (isPrivate ? "(Private " : "") + fileType + " from " + sender + ": " + filename + " ";
                doc.insertString(doc.getLength(), icon + " " + header, null);

                // Create button panel with both Open and Download buttons
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

                // Open button (only for supported formats)
                if (canOpenFile(filename)) {
                    JButton openBtn = new JButton("Open");
                    openBtn.setMargin(new Insets(2, 8, 2, 8));
                    openBtn.addActionListener(e -> openFile(fileBytes, filename));
                    buttonPanel.add(openBtn);
                }

                // Download button (always shown)
                JButton downloadBtn = new JButton("Download");
                downloadBtn.setMargin(new Insets(2, 8, 2, 8));
                downloadBtn.addActionListener(e -> downloadFile(fileBytes, filename));
                buttonPanel.add(downloadBtn);

                // Add file size info
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

    private boolean canOpenFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".txt") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".wav") ||
                lower.endsWith(".mp3");
    }

    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "📄";
        if (lower.endsWith(".txt")) return "📝";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📋";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📊";
        if (lower.endsWith(".zip") || lower.endsWith(".rar")) return "📦";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) return "🎵";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi")) return "🎬";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif")) return "🖼️";
        return "📎"; // generic file icon
    }

    private void addDownloadButton(byte[] data, String filename) {
        JButton downloadBtn = new JButton("Download");
        downloadBtn.setMargin(new Insets(2, 8, 2, 8));
        downloadBtn.setFont(new Font("Arial", Font.PLAIN, 10));
        downloadBtn.addActionListener(e -> downloadFile(data, filename));

        textPane.insertComponent(downloadBtn);
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
                            "File Saved",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Desktop is not supported. File saved to: " + temp.getAbsolutePath(),
                        "File Saved",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open file: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
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
                        "Download Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save file: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void sendTextMessage() {
        try {
            String message = textField.getText().trim();
            if (message.isEmpty()) return;
            String target = userList.getSelectedValue();
            if (target == null || target.equals("All")) {
                out.writeUTF("MSG_ALL:" + message);
                out.flush();
                appendText("Me: " + message + "\n");
            } else {
                out.writeUTF("MSG_TO:" + target + ":" + message);
                out.flush();
                appendText("(Private to " + target + "): " + message + "\n");
            }
            textField.setText("");
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("⚠️ Failed to send message: " + ex.getMessage() + "\n");
        }
    }

    private void sendImageFile(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String target = userList.getSelectedValue();
            if (target == null || target.equals("All")) {
                out.writeUTF("IMG_ALL:" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayImage(data, "Me", file.getName(), false);
            } else {
                out.writeUTF("IMG_TO:" + target + ":" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayImage(data, "Me (to " + target + ")", file.getName(), true);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("⚠️ Failed to send image: " + ex.getMessage() + "\n");
        }
    }

    private void sendPdfFile(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String target = userList.getSelectedValue();
            if (target == null || target.equals("All")) {
                out.writeUTF("PDF_ALL:" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayFile(data, "Me", file.getName(), "PDF", false);
            } else {
                out.writeUTF("PDF_TO:" + target + ":" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayFile(data, "Me (to " + target + ")", file.getName(), "PDF", true);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("⚠️ Failed to send PDF: " + ex.getMessage() + "\n");
        }
    }

    private void sendAudioFile(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String target = userList.getSelectedValue();
            if (target == null || target.equals("All")) {
                out.writeUTF("AUDIO_ALL:" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayAudio(data, "Me", file.getName(), false);
            } else {
                out.writeUTF("AUDIO_TO:" + target + ":" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayAudio(data, "Me (to " + target + ")", file.getName(), true);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("⚠️ Failed to send audio: " + ex.getMessage() + "\n");
        }
    }

    private void sendGenericFile(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String target = userList.getSelectedValue();
            if (target == null || target.equals("All")) {
                out.writeUTF("FILE_ALL:" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayFile(data, "Me", file.getName(), "File", false);
            } else {
                out.writeUTF("FILE_TO:" + target + ":" + clientName + ":" + file.getName() + ":" + data.length);
                out.flush();
                out.write(data);
                out.flush();
                displayFile(data, "Me (to " + target + ")", file.getName(), "File", true);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            appendText("⚠️ Failed to send file: " + ex.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClient("127.0.0.1", 12345));
    }
}