package ui;

import TCP.Server.TcpChatServerUI;
import UDP.Server.UdpChatServerUI;
import javax.swing.*;
import java.awt.*;
import java.net.SocketException;

public class ServerLauncher extends JFrame {

    public ServerLauncher() {
        super("Server Launcher");
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

        JLabel title = new JLabel("Choose server to start:", SwingConstants.CENTER);
        panel.add(title);

        JButton tcpButton = new JButton("TCP Server");
        tcpButton.addActionListener(e -> launchTCPServer());
        panel.add(tcpButton);

        JButton udpButton = new JButton("UDP Server");
        udpButton.addActionListener(e -> launchUDPServer());
        panel.add(udpButton);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
    }

    private void launchTCPServer() {
        try {
            final int[] portArr = { 12345 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter TCP server port (default: 12345):", "12345");
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

            SwingUtilities.invokeLater(() -> {
                try {
                    new TcpChatServerUI(port);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to start TCP server: " + e.getMessage(),
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

    private void launchUDPServer() {
        try {
            final int[] portArr = { 5000 };
            String portStr = JOptionPane.showInputDialog(this,
                    "Enter UDP server port (default: 5000):", "5000");
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

            SwingUtilities.invokeLater(() -> {
                try {
                    UdpChatServerUI serverUI = new UDP.Server.UdpChatServerUI(port);
                    serverUI.setVisible(true);
                } catch (SocketException e) {
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
            }
            new ServerLauncher();
        });
    }
}
