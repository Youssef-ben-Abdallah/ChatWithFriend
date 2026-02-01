package ui;

import core.net.LogSink;
import core.util.SwingUtil;

import javax.swing.*;

public final class UiLogSink implements LogSink {
    private final JTextArea area;

    public UiLogSink(JTextArea area) { this.area = area; }

    @Override public void log(String line) {
        SwingUtil.ui(() -> {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }
}
