package core.util;

import javax.swing.*;

public final class SwingUtil {
    private SwingUtil() {}

    public static void ui(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
