package ui.server;

import core.net.ServerControlApi;
import core.util.SwingUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Reusable server dashboard:
 * - Connected clients list
 * - Kick selected user
 * - Status + count
 */
public final class ServerDashboard extends JPanel {

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> clients = new JList<>(model);

    private final JLabel status = new JLabel("Server: stopped");
    private final JLabel count = new JLabel("Clients: 0");

    public ServerDashboard() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        clients.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel top = new JPanel(new GridLayout(2, 1, 4, 4));
        top.add(status);
        top.add(count);

        JButton kick = new JButton("Kick selected");
        kick.addActionListener(e -> onKick());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(kick, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(clients), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private ServerControlApi server;

    public void bind(ServerControlApi server) {
        this.server = server;
        setRunning(server != null && server.isRunning());
        if (server != null) {
            setClients(server.getClients());
        } else {
            setClients(List.of());
        }
    }

    public void setRunning(boolean running) {
        SwingUtil.ui(() -> status.setText("Server: " + (running ? "running" : "stopped")));
    }

    public void setClients(List<String> names) {
        SwingUtil.ui(() -> {
            model.clear();
            for (String n : names) model.addElement(n);
            count.setText("Clients: " + names.size());
        });
    }

    private void onKick() {
        if (server == null || !server.isRunning()) return;
        String target = clients.getSelectedValue();
        if (target == null) return;

        String reason = JOptionPane.showInputDialog(this, "Reason (optional):", "Removed by server");
        server.kick(target, reason == null ? "" : reason);
    }
}
