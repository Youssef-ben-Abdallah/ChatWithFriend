package ui;

import TCP.Client.TcpChatClientCore;
import TCP.Server.TcpChatServerCore;
import UDP.Client.UdpChatClientCore;
import javax.swing.*;
import java.awt.*;

/**
 * Main launcher window that allows users to choose between TCP and UDP
 * protocols.
 */
public class AppLauncher extends JFrame {

    public AppLauncher() {
        super("Chat Application Launcher");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        add(mainPanel);

        // Title
        JLabel titleLabel = new JLabel("Chat Application", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Protocol selection panel
        JPanel protocolPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        protocolPanel.setBorder(BorderFactory.createTitledBorder("Select Protocol"));

        // TCP Button
        JButton tcpButton = new JButton("TCP Protocol");
        tcpButton.setFont(new Font("Arial", Font.PLAIN, 16));
        tcpButton.setPreferredSize(new Dimension(200, 60));
        tcpButton.addActionListener(e -> launchTCP());

        // UDP Button
        JButton udpButton = new JButton("UDP Protocol");
        udpButton.setFont(new Font("Arial", Font.PLAIN, 16));
        udpButton.setPreferredSize(new Dimension(200, 60));
        udpButton.addActionListener(e -> launchUDP());

        protocolPanel.add(tcpButton);
        protocolPanel.add(udpButton);

        mainPanel.add(protocolPanel, BorderLayout.CENTER);

        // Info label
        JLabel infoLabel = new JLabel(
                "<html><center>Choose a protocol to start chatting.<br>" +
                        "Both protocols support messaging, file sharing, images, and voice messages.</center></html>",
                JLabel.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        mainPanel.add(infoLabel, BorderLayout.SOUTH);
    }

    private void launchTCP() {
        // Show choice dialog for Server or Client
        String[] options = { "Server", "Client", "Cancel" };
        int choice = JOptionPane.showOptionDialog(
                this,
                "Do you want to start a TCP Server or Client?",
                "TCP Protocol",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]);

        if (choice == 0) {
            // Launch TCP Server
            launchTCPServer();
        } else if (choice == 1) {
            // Launch TCP Client
            launchTCPClient();
        }
    }

    private void launchUDP() {
        // Show choice dialog for Server or Client
        String[] options = { "Server", "Client", "Cancel" };
        int choice = JOptionPane.showOptionDialog(
                this,
                "Do you want to start a UDP Server or Client?",
                "UDP Protocol",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]);

        if (choice == 0) {
            // Launch UDP Server
            launchUDPServer();
        } else if (choice == 1) {
            // Launch UDP Client
            launchUDPClient();
        }
    }

    private void launchTCPServer() {
        try {
            final int[] portArr = { 12345 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter server port (default: 12345):", "12345");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    portArr[0] = Integer.parseInt(portStr.trim());
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid port number. Using default 12345.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    portArr[0] = 12345;
                }
            }

            final int port = portArr[0];

            // Launch TCP Server UI which starts the server itself
            SwingUtilities.invokeLater(() -> {
                try {
                    new TCP.Server.TcpChatServerUI(port);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to start server UI: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                dispose();
            });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error launching TCP server: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchTCPClient() {
        try {
            // Get connection details
            String serverAddressInput = JOptionPane.showInputDialog(this,
                    "Enter server address (default: 127.0.0.1):", "127.0.0.1");
            final String serverAddress = (serverAddressInput == null || serverAddressInput.trim().isEmpty())
                    ? "127.0.0.1"
                    : serverAddressInput;

            final int[] portArr = { 12345 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter server port (default: 12345):", "12345");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    portArr[0] = Integer.parseInt(portStr.trim());
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid port number. Using default 12345.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    portArr[0] = 12345;
                }
            }
            final int port = portArr[0];

            String clientNameInput = JOptionPane.showInputDialog(this,
                    "Enter your name:", "Anonymous");
            final String clientName = (clientNameInput == null || clientNameInput.trim().isEmpty())
                    ? "Anonymous"
                    : clientNameInput;

            TcpChatClientCore clientCore = new TCP.Client.TcpChatClientCore();
            UnifiedChatClientUI clientUI = new UnifiedChatClientUI(clientCore, clientName, serverAddress, port);
            clientUI.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error launching TCP client: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchUDPServer() {
        try {
            final int[] portArr = { 5000 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter server port (default: 5000):", "5000");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    portArr[0] = Integer.parseInt(portStr.trim());
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid port number. Using default 5000.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    portArr[0] = 5000;
                }
            }
            final int port = portArr[0];

            // Launch UDP Server UI
            SwingUtilities.invokeLater(() -> {
                try {
                    UDP.Server.UdpChatServerUI serverUI = new UDP.Server.UdpChatServerUI(port);
                    serverUI.setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to start UDP server: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                dispose();
            });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error launching UDP server: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void launchUDPClient() {
        try {
            String serverAddressInput = JOptionPane.showInputDialog(this,
                    "Enter server address (default: 127.0.0.1):", "127.0.0.1");
            final String serverAddress = (serverAddressInput == null || serverAddressInput.trim().isEmpty())
                    ? "127.0.0.1"
                    : serverAddressInput;

            final int[] portArr = { 5000 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter server port (default: 5000):", "5000");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    portArr[0] = Integer.parseInt(portStr.trim());
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid port number. Using default 5000.",
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    portArr[0] = 5000;
                }
            }
            final int port = portArr[0];

            String clientNameInput = JOptionPane.showInputDialog(this,
                    "Enter your name:", "Anonymous");
            final String clientName = (clientNameInput == null || clientNameInput.trim().isEmpty())
                    ? "Anonymous"
                    : clientNameInput;

            UdpChatClientCore clientCore = new UdpChatClientCore();
            UnifiedChatClientUI clientUI = new UnifiedChatClientUI(clientCore, clientName, serverAddress, port);
            clientUI.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error launching UDP client: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Use default look and feel
            }
            new AppLauncher();
        });
    }
}
