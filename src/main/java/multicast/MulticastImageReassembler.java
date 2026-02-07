package multicast;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles chunked image messages for multicast receivers.
 */
final class MulticastImageReassembler {
    private static class ImageChunk {
        String filename;
        int totalChunks;
        Map<Integer, String> chunks = new ConcurrentHashMap<>();
    }

    private final Map<String, ImageChunk> pending = new ConcurrentHashMap<>();

    void onImageStart(String filename, String id, int totalChunks) {
        ImageChunk chunk = new ImageChunk();
        chunk.filename = filename;
        chunk.totalChunks = totalChunks;
        pending.put(id, chunk);
    }

    void onImageChunk(String id, int chunkIndex, String base64Chunk) {
        ImageChunk chunk = pending.get(id);
        if (chunk != null) {
            chunk.chunks.put(chunkIndex, base64Chunk);
        }
    }

    ImageResult onImageEnd(String id) {
        ImageChunk chunk = pending.remove(id);
        if (chunk == null) return null;

        // Check if all chunks are present
        if (chunk.chunks.size() != chunk.totalChunks) {
            return null; // Missing chunks
        }

        // Reassemble Base64 string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunk.totalChunks; i++) {
            String part = chunk.chunks.get(i);
            if (part == null) return null;
            sb.append(part);
        }

        // Decode Base64 to bytes
        try {
            byte[] imageBytes = Base64.getDecoder().decode(sb.toString());
            return new ImageResult(chunk.filename, imageBytes);
        } catch (Exception e) {
            return null;
        }
    }

    static class ImageResult {
        final String filename;
        final byte[] imageData;

        ImageResult(String filename, byte[] imageData) {
            this.filename = filename;
            this.imageData = imageData;
        }
    }
}
