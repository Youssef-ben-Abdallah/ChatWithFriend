package ui;

import core.net.ServerControlListener;
import tcp.TcpClientCore;
import tcp.TcpServerCore;
import ui.server.ServerDashboard;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** TCP mode screen: server dashboard + live log + create clients. */
public final class TcpModePanel extends ModePanel {

    private final JTextArea logArea = new JTextArea();
    private final UiLogSink log = new UiLogSink(logArea);

    private final ServerDashboard dashboard = new ServerDashboard();

    private TcpServerCore server;
    private final List<ChatClientWindow> clients = new ArrayList<>();

    private int serverPort = 12345;

    public TcpModePanel(HomeScreen home) {
        super(home);
        setLayout(new BorderLayout(10, 10));

        logArea.setEditable(false);
        logArea.setFont(Theme.monoFont());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton startServer = new JButton("Create Server (TCP)");
        JButton stopServer = new JButton("Stop Server");
        JButton createClient = new JButton("Create Client (TCP)");

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

        log.log("TCP Mode ready. Default port: " + serverPort);
    }

    private void onStartServer() {
        if (server != null && server.isRunning()) {
            log.log("[TCP] Server already running on port " + serverPort);
            return;
        }

        String portStr = JOptionPane.showInputDialog(this, "Server port:", String.valueOf(serverPort));
        if (portStr == null) return;
        try { serverPort = Integer.parseInt(portStr.trim()); }
        catch (Exception e) { log.log("[TCP] Invalid port."); return; }

        server = new TcpServerCore(serverPort, log);
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
            log.log("[TCP] Start failed: " + e.getMessage());
            server = null;
            dashboard.bind(null);
        }
    }

    private void onStopServer() {
        if (server == null) return;
        try { server.close(); } catch (Exception ignored) {}
        server = null;
        dashboard.bind(null);
        log.log("[TCP] Server stopped.");
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
        catch (Exception e) { log.log("[TCP] Invalid port."); return; }

        ChatClientWindow win = new ChatClientWindow("TCP Client - " + name.trim());
        TcpClientCore core = new TcpClientCore(host.trim(), port, name.trim(), win, log);
        win.attachClient(core);

        clients.add(win);
        win.connectAndShow();
    }

    @Override public void onBack() {
        for (ChatClientWindow w : new ArrayList<>(clients)) {
            try { w.dispose(); } catch (Exception ignored) {}
        }
        clients.clear();

        onStopServer();
        log.log("[TCP] Stopped all.");
    }
}
