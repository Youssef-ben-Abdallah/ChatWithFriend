package ui;

import javax.swing.*;

/** Base class for TCP/UDP panels; handles clean shutdown on Back. */
public abstract class ModePanel extends JPanel {
    protected final HomeScreen home;

    protected ModePanel(HomeScreen home) { this.home = home; }

    /** Called when user presses Back. Must stop servers and clients safely. */
    public abstract void onBack();
}
