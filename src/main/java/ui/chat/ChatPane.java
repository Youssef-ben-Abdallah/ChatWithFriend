package ui.chat;

import core.audio.PcmPlayer;
import core.util.SwingUtil;

import javax.sound.sampled.AudioFormat;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;

/**
 * Reusable chat view that can display:
 * - text lines
 * - inline images
 * - attachments with Open/Save
 * - voice playback button
 */
public final class ChatPane extends JPanel {
    private final JTextPane pane = new JTextPane();
    private final StyledDocument doc = pane.getStyledDocument();

    public ChatPane() {
        setLayout(new BorderLayout());
        pane.setEditable(false);
        add(new JScrollPane(pane), BorderLayout.CENTER);
    }

    public void addText(String line) {
        SwingUtil.ui(() -> {
            try {
                doc.insertString(doc.getLength(), line + "\n", null);
                pane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    public void addImage(String title, ImageIcon icon) {
        SwingUtil.ui(() -> {
            try {
                doc.insertString(doc.getLength(), title + "\n", null);
                // Scale to a reasonable width
                Image img = icon.getImage();
                int maxW = 360;
                int w = icon.getIconWidth();
                int h = icon.getIconHeight();
                if (w > maxW) {
                    int newH = (int) (h * (maxW / (double) w));
                    img = img.getScaledInstance(maxW, newH, Image.SCALE_SMOOTH);
                }
                JLabel lbl = new JLabel(new ImageIcon(img));
                insertComponent(lbl);
                doc.insertString(doc.getLength(), "\n", null);
                pane.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                addText("Image display error: " + e.getMessage());
            }
        });
    }

    public void addFileAttachment(String title, String fileName, byte[] bytes) {
        SwingUtil.ui(() -> {
            try {
                doc.insertString(doc.getLength(), title + "\n", null);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                row.add(new JLabel("[file] " + fileName + " (" + bytes.length + " bytes)"));

                JButton open = new JButton("Open");
                JButton save = new JButton("Save As...");
                row.add(open);
                row.add(save);

                open.addActionListener(e -> {
                    try {
                        File tmp = File.createTempFile("recv_", "_" + safe(fileName));
                        tmp.deleteOnExit();
                        Files.write(tmp.toPath(), bytes);
                        Desktop.getDesktop().open(tmp);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Open failed: " + ex.getMessage());
                    }
                });

                save.addActionListener(e -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setSelectedFile(new File(fileName));
                    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                        try { Files.write(fc.getSelectedFile().toPath(), bytes); }
                        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage()); }
                    }
                });

                insertComponent(row);
                doc.insertString(doc.getLength(), "\n", null);
                pane.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                addText("Attachment error: " + e.getMessage());
            }
        });
    }

    public void addVoice(String title, AudioFormat format, byte[] pcmBytes) {
        SwingUtil.ui(() -> {
            try {
                doc.insertString(doc.getLength(), title + "\n", null);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                row.add(new JLabel("[voice] Voice message (" + pcmBytes.length + " bytes PCM)"));
                JButton play = new JButton("Play");
                row.add(play);

                play.addActionListener(e -> {
                    new Thread(() -> {
                        try {
                            PcmPlayer.play(format, pcmBytes);
                        } catch (Exception ex) {
                            SwingUtil.ui(() -> JOptionPane.showMessageDialog(this, "Play failed: " + ex.getMessage()));
                        }
                    }, "VoicePlay").start();
                });

                insertComponent(row);
                doc.insertString(doc.getLength(), "\n", null);
                pane.setCaretPosition(doc.getLength());
            } catch (Exception e) {
                addText("Voice UI error: " + e.getMessage());
            }
        });
    }

    private void insertComponent(JComponent c) throws BadLocationException {
        pane.setCaretPosition(doc.getLength());
        pane.insertComponent(c);
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
