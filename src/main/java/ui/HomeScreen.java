package ui;

import javax.swing.*;
import java.awt.*;

public final class HomeScreen extends JFrame {

    public HomeScreen() {
        super("Simple Networking App (TCP + UDP + Multicast)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(520, 420);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("Java Simple Networking App", SwingConstants.CENTER);
        title.setFont(Theme.titleFont());

        JButton tcp = Theme.bigButton("TCP");
        JButton udp = Theme.bigButton("UDP");
        JButton multicast = Theme.bigButton("Multicast");

        tcp.addActionListener(e -> openTcp());
        udp.addActionListener(e -> openUdp());
        multicast.addActionListener(e -> openMulticast());

        JPanel center = new JPanel(new GridLayout(3, 1, 18, 18));
        center.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        center.add(tcp);
        center.add(udp);
        center.add(multicast);

        setLayout(new BorderLayout());
        add(title, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private void openTcp() {
        new ModeFrame(this, "TCP Mode", new TcpModePanel(this)).setVisible(true);
        setVisible(false);
    }

    private void openUdp() {
        new ModeFrame(this, "UDP Mode", new UdpModePanel(this)).setVisible(true);
        setVisible(false);
    }

    private void openMulticast() {
        new ModeFrame(this, "Multicast Mode", new MulticastModePanel(this)).setVisible(true);
        setVisible(false);
    }
}
