package ui;

import javax.swing.*;
import java.awt.*;

/** Shared frame wrapper that provides a back arrow and hosts a mode panel. */
public final class ModeFrame extends JFrame {
    public ModeFrame(HomeScreen home, String title, ModePanel panel) {
        super(title);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);

        JButton back = new JButton("<- Back");
        back.addActionListener(e -> {
            panel.onBack();
            dispose();
            home.setVisible(true);
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(back, BorderLayout.WEST);
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(Theme.titleFont());
        top.add(t, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
    }
}
