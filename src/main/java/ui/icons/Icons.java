package ui.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * Tiny vector icons (no image files needed).
 * Color uses the component's foreground (Swing will paint it).
 */
public final class Icons {
    private Icons() {}

    public static Icon send(int size) { return new SendIcon(size); }
    public static Icon image(int size) { return new ImageIcon(size); }
    public static Icon file(int size) { return new FileIcon(size); }
    public static Icon mic(int size) { return new MicIcon(size); }
    public static Icon stop(int size) { return new StopIcon(size); }
    public static Icon play(int size) { return new PlayIcon(size); }

    private abstract static class Base implements Icon {
        final int s;
        Base(int s) { this.s = s; }
        @Override public int getIconWidth() { return s; }
        @Override public int getIconHeight() { return s; }
        Color fg(Component c) { return (c != null && c.getForeground() != null) ? c.getForeground() : Color.DARK_GRAY; }
    }

    /** Paper-plane send icon. */
    private static final class SendIcon extends Base {
        SendIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));

            int w = s, h = s;
            int ox = x, oy = y;

            GeneralPath p = new GeneralPath();
            p.moveTo(ox + 2, oy + h/2);
            p.lineTo(ox + w - 2, oy + 2);
            p.lineTo(ox + w*0.65, oy + h/2);
            p.lineTo(ox + w - 2, oy + h - 2);
            p.closePath();

            g2.fill(p);

            // inner cut line
            g2.setColor(new Color(255,255,255,110));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ox + 6, oy + h/2, ox + (int)(w*0.63), oy + h/2);

            g2.dispose();
        }
    }

    /** Photo icon (mountain + sun). */
    private static final class ImageIcon extends Base {
        ImageIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));
            g2.setStroke(new BasicStroke(2f));

            int ox=x, oy=y, w=s, h=s;
            g2.drawRoundRect(ox+2, oy+2, w-4, h-4, 4, 4);
            g2.fillOval(ox + (int)(w*0.62), oy + (int)(h*0.18), 4, 4);

            GeneralPath m = new GeneralPath();
            m.moveTo(ox + 5, oy + h - 6);
            m.lineTo(ox + (int)(w*0.40), oy + (int)(h*0.55));
            m.lineTo(ox + (int)(w*0.52), oy + (int)(h*0.68));
            m.lineTo(ox + (int)(w*0.75), oy + (int)(h*0.45));
            m.lineTo(ox + w - 5, oy + h - 6);
            m.closePath();
            g2.fill(m);

            g2.dispose();
        }
    }

    /** Document icon. */
    private static final class FileIcon extends Base {
        FileIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));
            g2.setStroke(new BasicStroke(2f));

            int ox=x, oy=y, w=s, h=s;
            g2.drawRoundRect(ox+3, oy+2, w-6, h-4, 4, 4);
            g2.drawLine(ox + (int)(w*0.60), oy+2, ox + w-3, oy + (int)(h*0.35));
            g2.drawLine(ox + (int)(w*0.60), oy+2, ox + (int)(w*0.60), oy + (int)(h*0.35));
            g2.drawLine(ox + (int)(w*0.60), oy + (int)(h*0.35), ox + w-3, oy + (int)(h*0.35));

            g2.setStroke(new BasicStroke(1.6f));
            g2.drawLine(ox+6, oy + (int)(h*0.52), ox + w-6, oy + (int)(h*0.52));
            g2.drawLine(ox+6, oy + (int)(h*0.66), ox + (int)(w*0.78), oy + (int)(h*0.66));

            g2.dispose();
        }
    }

    /** Microphone icon. */
    private static final class MicIcon extends Base {
        MicIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int ox=x, oy=y, w=s, h=s;
            int cx = ox + w/2;

            g2.drawRoundRect(cx-4, oy+3, 8, 10, 6, 6);
            g2.drawLine(cx, oy+13, cx, oy+16);
            g2.drawLine(cx-5, oy+16, cx+5, oy+16);

            // U shape
            g2.drawArc(cx-7, oy+8, 14, 12, 200, 140);

            g2.dispose();
        }
    }

    /** Stop icon (square). */
    private static final class StopIcon extends Base {
        StopIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));
            int pad = 4;
            g2.fillRoundRect(x+pad, y+pad, s-2*pad, s-2*pad, 4, 4);
            g2.dispose();
        }
    }

    /** Play icon (triangle). */
    private static final class PlayIcon extends Base {
        PlayIcon(int s) { super(s); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fg(c));

            int ox=x, oy=y, w=s, h=s;
            GeneralPath p = new GeneralPath();
            p.moveTo(ox + (int)(w*0.35), oy + (int)(h*0.25));
            p.lineTo(ox + (int)(w*0.35), oy + (int)(h*0.75));
            p.lineTo(ox + (int)(w*0.75), oy + (int)(h*0.50));
            p.closePath();
            g2.fill(p);

            g2.dispose();
        }
    }
}
