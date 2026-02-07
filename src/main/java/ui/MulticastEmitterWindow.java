package ui;

import multicast.MulticastEmitter;
import ui.chat.ChatPane;
import ui.icons.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/** Window for multicast emitter. */
public final class MulticastEmitterWindow extends JFrame {
    private final MulticastEmitter emitter;
    private final ChatPane chatPane = new ChatPane();
    private final JTextField messageField = new JTextField();

    public MulticastEmitterWindow(MulticastEmitter emitter, String address, int port) {
        super("Multicast Emitter - " + address + ":" + port);
        this.emitter = emitter;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);

        JButton sendButton = Theme.squareIconButton(Icons.send(18), "Send text message");
        sendButton.addActionListener(e -> onSendText());

        JButton sendImageButton = Theme.squareIconButton(Icons.image(18), "Send image");
        sendImageButton.addActionListener(e -> onSendImage());

        messageField.addActionListener(e -> onSendText());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.add(sendImageButton);
        buttonPanel.add(sendButton);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputPanel.add(new JLabel("Message:"), BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(chatPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    if (emitter != null) emitter.close();
                } catch (Exception ignored) {}
            }
        });

        chatPane.addText("Multicast Emitter ready. Type a message and click Send, or click the image button to send an image.");
    }

    private void onSendText() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        try {
            emitter.send(message);
            chatPane.addText("Me: " + message);
            messageField.setText("");
        } catch (Exception e) {
            chatPane.addText("Send error: " + e.getMessage());
        }
    }

    private void onSendImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Image");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File imageFile = fc.getSelectedFile();
        try {
            emitter.sendImage(imageFile);
            chatPane.addText("Me: sent image " + imageFile.getName());
            // Also show the image in the emitter window
            ImageIcon icon = new ImageIcon(imageFile.getAbsolutePath());
            chatPane.addImage("Me (sent): " + imageFile.getName(), icon);
        } catch (Exception e) {
            chatPane.addText("Image send error: " + e.getMessage());
        }
    }
}
