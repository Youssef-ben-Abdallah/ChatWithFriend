package udp;

import core.model.BinaryKind;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reassembles BIN_* transfers (images/files). Voice is streamed directly via callbacks. */
public final class UdpReassembler {

    public static final class Incoming {
        public final String from;
        public final String to;
        public final String id;
        public final BinaryKind kind;
        public final String name; // filename
        public final int total;
        public final byte[][] parts;
        public final boolean[] got;

        public Incoming(String from, String to, String id, BinaryKind kind, String name, int total) {
            this.from = from; this.to = to; this.id = id; this.kind = kind; this.name = name; this.total = total;
            this.parts = new byte[total][];
            this.got = new boolean[total];
        }

        public boolean complete() {
            for (boolean b : got) if (!b) return false;
            return true;
        }

        public byte[] join() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < total; i++) out.write(parts[i]);
            return out.toByteArray();
        }
    }

    private final Map<String, Incoming> bin = new ConcurrentHashMap<>();

    public void onBinStart(String from, String to, String payload) {
        // id;KIND;filename;totalChunks
        String[] p = payload.split(";", 4);
        if (p.length < 4) return;

        String id = p[0];
        BinaryKind kind;
        try { kind = BinaryKind.valueOf(p[1]); } catch (Exception e) { return; }

        String filename = p[2];
        int total = parseInt(p[3]);
        if (total <= 0) return;

        bin.put(id, new Incoming(from, to, id, kind, filename, total));
    }

    public void onBinChunk(String payload) {
        // id;idx;base64
        String[] p = payload.split(";", 3);
        if (p.length < 3) return;

        String id = p[0];
        int idx = parseInt(p[1]);

        Incoming in = bin.get(id);
        if (in == null || idx < 0 || idx >= in.total || in.got[idx]) return;

        try {
            byte[] part = Base64.getDecoder().decode(p[2]);
            in.parts[idx] = part;
            in.got[idx] = true;
        } catch (Exception ignored) {}
    }

    public Incoming onBinEnd(String id) {
        return bin.remove(id.trim());
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }
}
