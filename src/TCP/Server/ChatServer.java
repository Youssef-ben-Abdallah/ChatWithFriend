package TCP.Server;
import javax.swing.*;

import java.awt.*;

public class ChatServer {
    private static final int PORT = 12345; // Changed from 123 to 12345 (common port)
    private TcpChatServerCore serverCore;

    // GUI components
    private JFrame frame;
    private JTextArea textArea;
    private JLabel clientsLabel;

    public ChatServer() {
        setupGUI();
        
        // Get singleton instance of server core
        serverCore = TcpChatServerCore.getInstance();
        
        // Set up listeners for UI updates
        serverCore.setOnLog(this::appendMessage);
        serverCore.setOnClientListUpdated(this::updateClientsList);
        
        // Start server
        if (!serverCore.start(PORT)) {
            appendMessage("Failed to start server - may already be running!");
            JOptionPane.showMessageDialog(frame, 
                "Server is already running! Only one server instance is allowed.",
                "Server Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void setupGUI() {
        frame = new JFrame("Java Chat Server");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        clientsLabel = new JLabel("Clients: None");
        frame.add(clientsLabel, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setEditable(false);
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private void updateClientsList(java.util.List<String> clients) {
        SwingUtilities.invokeLater(() -> {
            if (clients.isEmpty()) {
                clientsLabel.setText("Clients: None");
            } else {
                StringBuilder sb = new StringBuilder("Clients: ");
                for (String name : clients) {
                    sb.append(name).append(", ");
                }
                if (sb.length() > 9) sb.setLength(sb.length() - 2);
                clientsLabel.setText(sb.toString());
            }
        });
    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            // Auto-scroll to bottom
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // All server logic methods have been moved to TcpChatServerCore
    // ChatServer is now just a UI wrapper

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServer());
    }
}