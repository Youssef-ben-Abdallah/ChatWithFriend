package ui;

import multicast.MulticastEmitter;
import multicast.MulticastRecepteur;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Multicast mode screen: create emitter and receivers. */
public final class MulticastModePanel extends ModePanel {

    private final JTextArea logArea = new JTextArea();
    private final UiLogSink log = new UiLogSink(logArea);

    private MulticastEmitter emitter;
    private final List<MulticastRecepteur> receivers = new ArrayList<>();
    private final List<MulticastReceiverWindow> receiverWindows = new ArrayList<>();

    private final List<MulticastEmitterWindow> emitterWindows = new ArrayList<>();

    private String multicastAddress = "230.0.0.1";
    private int port = 12347;
    private int receiverCounter = 1;

    public MulticastModePanel(HomeScreen home) {
        super(home);
        setLayout(new BorderLayout(10, 10));

        logArea.setEditable(false);
        logArea.setFont(Theme.monoFont());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton createEmitter = new JButton("Create Emitter");
        JButton createReceiver = new JButton("Create Receiver");
        JButton stopAll = new JButton("Stop All");

        actions.add(createEmitter);
        actions.add(createReceiver);
        actions.add(stopAll);

        createEmitter.addActionListener(e -> onCreateEmitter());
        createReceiver.addActionListener(e -> onCreateReceiver());
        stopAll.addActionListener(e -> onStopAll());

        add(actions, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        log.log("Multicast Mode ready. Default: " + multicastAddress + ":" + port);
    }

    private void onCreateEmitter() {
        if (emitter != null && emitter.isRunning()) {
            log.log("[MULTICAST] Emitter already running");
            return;
        }

        String addr = JOptionPane.showInputDialog(this, "Multicast address:", multicastAddress);
        if (addr == null) return;
        addr = addr.trim();
        if (addr.isEmpty()) return;

        String portStr = JOptionPane.showInputDialog(this, "Port:", String.valueOf(port));
        if (portStr == null) return;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (Exception e) {
            log.log("[MULTICAST] Invalid port.");
            return;
        }

        try {
            emitter = new MulticastEmitter(addr, port, log);
            emitter.start();
            multicastAddress = addr;

            MulticastEmitterWindow win = new MulticastEmitterWindow(emitter, multicastAddress, port);
            emitterWindows.add(win);
            win.setVisible(true);
        } catch (Exception e) {
            log.log("[MULTICAST] Emitter create failed: " + e.getMessage());
            emitter = null;
        }
    }

    private void onCreateReceiver() {
        String addr = JOptionPane.showInputDialog(this, "Multicast address:", multicastAddress);
        if (addr == null) return;
        addr = addr.trim();
        if (addr.isEmpty()) return;

        String portStr = JOptionPane.showInputDialog(this, "Port:", String.valueOf(port));
        if (portStr == null) return;
        int receiverPort;
        try {
            receiverPort = Integer.parseInt(portStr.trim());
        } catch (Exception e) {
            log.log("[MULTICAST] Invalid port.");
            return;
        }

        String defaultName = "Receiver" + receiverCounter;
        String name = JOptionPane.showInputDialog(this, "Receiver name:", defaultName);
        if (name == null || name.isBlank()) {
            name = defaultName;
        }

        try {
            MulticastRecepteur receiver = new MulticastRecepteur(addr, receiverPort, name.trim(), log);
            multicastAddress = addr;
            port = receiverPort;

            int receiverNumber = receiverCounter++;
            MulticastReceiverWindow win = new MulticastReceiverWindow(receiver, name.trim(), receiverNumber);
            
            receivers.add(receiver);
            receiverWindows.add(win);
            receiver.start();
            win.setVisible(true);
            
            log.log("[MULTICAST] Receiver created: " + name.trim() + " (#" + receiverNumber + ")");
        } catch (Exception e) {
            log.log("[MULTICAST] Receiver create failed: " + e.getMessage());
        }
    }

    private void onStopAll() {
        for (MulticastEmitterWindow w : new ArrayList<>(emitterWindows)) {
            try { w.dispose(); } catch (Exception ignored) {}
        }
        emitterWindows.clear();

        for (MulticastReceiverWindow w : new ArrayList<>(receiverWindows)) {
            try { w.dispose(); } catch (Exception ignored) {}
        }
        receiverWindows.clear();

        for (MulticastRecepteur receiver : new ArrayList<>(receivers)) {
            try { receiver.close(); } catch (Exception ignored) {}
        }
        receivers.clear();

        if (emitter != null) {
            try { emitter.close(); } catch (Exception ignored) {}
            emitter = null;
        }

        receiverCounter = 1; // Reset counter
        log.log("[MULTICAST] All stopped.");
    }

    @Override public void onBack() {
        onStopAll();
        log.log("[MULTICAST] Stopped all.");
    }
}
