package UDP.Server;

import UDP.Client.UdpChatClientUI;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.net.SocketException;
import java.util.List;

/**
 * UDP Server UI wrapper (same structure as TCP ChatServer).
 * Uses UdpChatServerCore singleton for server logic.
 */
public class UdpChatServerUI extends JFrame {
    private UdpChatServerCore serverCore;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> clientsList = new JList<>(model);
    private final JTextArea logArea = new JTextArea();

    public UdpChatServerUI(int port) throws SocketException {
        super("UDP Server (Port " + port + ")");
        
        buildUI();
        
        // Get singleton instance of server core
        serverCore = UdpChatServerCore.getInstance();
        
        // Set up listeners for UI updates
        serverCore.setOnLog(this::appendLog);
        serverCore.setOnClientListUpdated(this::updateClientsOnUI);
        
        // Start server
        if (!serverCore.start(port)) {
            appendLog("Failed to start server - may already be running!");
            JOptionPane.showMessageDialog(this, 
                "Server is already running! Only one server instance is allowed.",
                "Server Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void buildUI() {
        setSize(650, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        clientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        left.add(new JScrollPane(clientsList), BorderLayout.CENTER);

        JButton addClientBtn = new JButton("Add Client Window");
        addClientBtn.addActionListener(e -> createClientWindow());

        JButton kickBtn = new JButton("Kick Selected");
        kickBtn.addActionListener(e -> kickSelected());

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        leftBtns.add(addClientBtn);
        leftBtns.add(kickBtn);
        left.add(leftBtns, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(BorderFactory.createTitledBorder("Server Log"));
        right.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(280);
        add(split);
    }

    private void updateClientsOnUI(List<String> clients) {
        SwingUtilities.invokeLater(() -> {
            model.clear();
            for (String c : clients) model.addElement(c);
        });
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void createClientWindow() {
        String name = JOptionPane.showInputDialog(this, "Client name?");
        if (name == null || name.isBlank()) return;

        SwingUtilities.invokeLater(() -> 
            new UdpChatClientUI("127.0.0.1", serverCore.getPort(), name).setVisible(true));
    }

    private void kickSelected() {
        String selected = clientsList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a client first.");
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Reason (optional):", "Kicked by server");
        boolean ok = serverCore.kickClient(selected, reason);

        if (!ok) JOptionPane.showMessageDialog(this, "Client not found (maybe already disconnected).");
    }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : 5000;

        SwingUtilities.invokeLater(() -> {
            try {
                new UdpChatServerUI(port).setVisible(true);
            } catch (SocketException e) {
                JOptionPane.showMessageDialog(null, "Failed to start server: " + e.getMessage());
            }
        });
    }
}
