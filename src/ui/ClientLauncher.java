package ui;

import TCP.Client.TcpChatClientCore;
import UDP.Client.UdpChatClientCore;
import javax.swing.*;
import java.awt.*;

public class ClientLauncher extends JFrame {

    public ClientLauncher() {
        super("Client Launcher");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Choose client to start:", SwingConstants.CENTER);
        panel.add(title);

        JButton tcpButton = new JButton("TCP Client");
        tcpButton.addActionListener(e -> launchTCPClient());
        panel.add(tcpButton);

        JButton udpButton = new JButton("UDP Client");
        udpButton.addActionListener(e -> launchUDPClient());
        panel.add(udpButton);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
    }

    private void launchTCPClient() {
        try {
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

            TcpChatClientCore clientCore = new TcpChatClientCore();
            UnifiedChatClientUI clientUI = new UnifiedChatClientUI(clientCore, clientName, serverAddress, port);
            clientUI.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error launching TCP client: " + e.getMessage(),
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
            }
            new ClientLauncher();
        });
    }
}
