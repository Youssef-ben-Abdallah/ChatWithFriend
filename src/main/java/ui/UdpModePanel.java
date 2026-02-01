package ui;

import core.net.ServerControlListener;
import udp.UdpClientCore;
import udp.UdpServerCore;
import ui.server.ServerDashboard;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** UDP mode screen: server dashboard + live log + create clients. */
public final class UdpModePanel extends ModePanel {

    private final JTextArea logArea = new JTextArea();
    private final UiLogSink log = new UiLogSink(logArea);

    private final ServerDashboard dashboard = new ServerDashboard();

    private UdpServerCore server;
    private final List<ChatClientWindow> clients = new ArrayList<>();

    private int serverPort = 12346;

    public UdpModePanel(HomeScreen home) {
        super(home);
        setLayout(new BorderLayout(10, 10));

        logArea.setEditable(false);
        logArea.setFont(Theme.monoFont());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton startServer = new JButton("Create Server (UDP)");
        JButton stopServer = new JButton("Stop Server");
        JButton createClient = new JButton("Create Client (UDP)");

        actions.add(startServer);
        actions.add(stopServer);
        actions.add(createClient);

        startServer.addActionListener(e -> onStartServer());
        stopServer.addActionListener(e -> onStopServer());
        createClient.addActionListener(e -> onCreateClient());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                dashboard,
                new JScrollPane(logArea));
        split.setResizeWeight(0.25);

        add(actions, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        log.log("UDP Mode ready. Default port: " + serverPort);
    }

    private void onStartServer() {
        if (server != null && server.isRunning()) {
            log.log("[UDP] Server already running on port " + serverPort);
            return;
        }

        String portStr = JOptionPane.showInputDialog(this, "Server port:", String.valueOf(serverPort));
        if (portStr == null) return;
        try { serverPort = Integer.parseInt(portStr.trim()); }
        catch (Exception e) { log.log("[UDP] Invalid port."); return; }

        server = new UdpServerCore(serverPort, log);
        server.setListener(new ServerControlListener() {
            @Override public void onClientsChanged(List<String> names) {
                dashboard.setClients(names);
            }
        });

        try {
            server.start();
            dashboard.bind(server);
            dashboard.setRunning(true);
        } catch (Exception e) {
            log.log("[UDP] Start failed: " + e.getMessage());
            server = null;
            dashboard.bind(null);
        }
    }

    private void onStopServer() {
        if (server == null) return;
        try { server.close(); } catch (Exception ignored) {}
        server = null;
        dashboard.bind(null);
        log.log("[UDP] Server stopped.");
    }

    private void onCreateClient() {
        String name = JOptionPane.showInputDialog(this, "Client name:", "client" + (clients.size() + 1));
        if (name == null || name.isBlank()) return;

        String host = JOptionPane.showInputDialog(this, "Server host:", "127.0.0.1");
        if (host == null || host.isBlank()) return;

        String portStr = JOptionPane.showInputDialog(this, "Server port:", String.valueOf(serverPort));
        if (portStr == null) return;

        int port;
        try { port = Integer.parseInt(portStr.trim()); }
        catch (Exception e) { log.log("[UDP] Invalid port."); return; }

        try {
            ChatClientWindow win = new ChatClientWindow("UDP Client - " + name.trim());
            UdpClientCore core = new UdpClientCore(InetAddress.getByName(host.trim()), port, name.trim(), win, log);
            win.attachClient(core);

            clients.add(win);
            win.connectAndShow();
        } catch (Exception e) {
            log.log("[UDP] Client create failed: " + e.getMessage());
        }
    }

    @Override public void onBack() {
        for (ChatClientWindow w : new ArrayList<>(clients)) {
            try { w.dispose(); } catch (Exception ignored) {}
        }
        clients.clear();

        onStopServer();
        log.log("[UDP] Stopped all.");
    }
}
