package TCP.Server;

import javax.swing.*;

import java.awt.*;

public class TcpChatServerUI {
    private TcpChatServerCore serverCore;

    private JFrame frame;
    private JTextArea textArea;
    private DefaultListModel<String> model;
    private JList<String> clientsList;

    public TcpChatServerUI(int port) {
        setupGUI();
        serverCore = TcpChatServerCore.getInstance();
        serverCore.setOnLog(this::appendMessage);
        serverCore.setOnClientListUpdated(this::updateClientsList);

        if (!serverCore.start(port)) {
            appendMessage("Failed to start server - may already be running!");
            JOptionPane.showMessageDialog(frame,
                    "Server is already running! Only one server instance is allowed.",
                    "Server Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void setupGUI() {
        frame = new JFrame("TCP Server");
        frame.setSize(650, 560);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        // Left: connected clients list with kick button
        model = new DefaultListModel<>();
        clientsList = new JList<>(model);
        clientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        left.add(new JScrollPane(clientsList), BorderLayout.CENTER);

        JButton kickBtn = new JButton("Kick Selected");
        kickBtn.addActionListener(e -> kickSelected());
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        leftBtns.add(kickBtn);
        left.add(leftBtns, BorderLayout.SOUTH);

        // Right: server log text area
        textArea = new JTextArea();
        textArea.setEditable(false);
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(BorderFactory.createTitledBorder("Server Log"));
        right.add(new JScrollPane(textArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(280);
        frame.add(split);

        frame.setVisible(true);
    }

    private void updateClientsList(java.util.List<String> clients) {
        SwingUtilities.invokeLater(() -> {
            model.clear();
            for (String name : clients) {
                model.addElement(name);
            }
        });
    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private void kickSelected() {
        String selected = clientsList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            JOptionPane.showMessageDialog(frame, "Select a client first.");
            return;
        }
        String reason = JOptionPane.showInputDialog(frame, "Reason (optional):", "Kicked by server");
        boolean ok = serverCore.kickClient(selected, reason);
        if (!ok) {
            JOptionPane.showMessageDialog(frame, "Client not found (maybe already disconnected).");
        }
    }

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : 12345;
        SwingUtilities.invokeLater(() -> new TcpChatServerUI(port));
    }
}
