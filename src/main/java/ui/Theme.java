package ui;

import javax.swing.*;
import java.awt.*;

public final class Theme {
    private Theme() {}

    public static Font titleFont() { return new Font("SansSerif", Font.BOLD, 18); }
    public static Font monoFont()  { return new Font("Monospaced", Font.PLAIN, 12); }

    public static JButton bigButton(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 16));
        b.setPreferredSize(new Dimension(260, 60));
        return b;
    }

    /** Square icon button for chat actions (image/file/mic/send). */
    public static JButton squareIconButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(42, 42));
        b.setMinimumSize(new Dimension(42, 42));
        b.setMaximumSize(new Dimension(42, 42));
        b.setMargin(new Insets(0,0,0,0));
        return b;
    }
}
