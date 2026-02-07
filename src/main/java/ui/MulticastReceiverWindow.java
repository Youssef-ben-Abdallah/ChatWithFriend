package ui;

import multicast.MulticastMessageListener;
import multicast.MulticastRecepteur;
import ui.chat.ChatPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Window for multicast receivers. */
public final class MulticastReceiverWindow extends JFrame implements MulticastMessageListener {
    private final MulticastRecepteur receiver;
    private final ChatPane chatPane = new ChatPane();

    public MulticastReceiverWindow(MulticastRecepteur receiver, String name, int receiverNumber) {
        super("Multicast Receiver " + receiverNumber + " - " + name);
        this.receiver = receiver;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(chatPane, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    if (receiver != null) {
                        receiver.close();
                    }
                } catch (Exception ignored) {}
            }
        });

        chatPane.addText("Multicast Receiver " + receiverNumber + " (" + name + ") ready. Waiting for messages...");

        // Set message listener
        if (receiver != null) {
            receiver.setMessageListener(this);
        }
    }

    @Override
    public void onMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatPane.addText("Received text: " + message);
        });
    }

    @Override
    public void onImage(String filename, byte[] imageData) {
        SwingUtilities.invokeLater(() -> {
            try {
                ImageIcon icon = new ImageIcon(imageData);
                chatPane.addImage("Received image: " + filename, icon);
            } catch (Exception e) {
                chatPane.addText("Error displaying image " + filename + ": " + e.getMessage());
            }
        });
    }
}
