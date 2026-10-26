package za.co.focigroup.focitrace;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Multipart {
    public record Part(String name, String filename, String contentType, byte[] data) {
        public String text() { return new String(data, StandardCharsets.UTF_8).trim(); }
    }

    private Multipart() {}

    public static Map<String,Part> parse(HttpExchange ex, int maxBytes) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) throw new IOException("Expected multipart/form-data");
        String boundary = null;
        for (String p : ct.split(";")) {
            p = p.trim();
            if (p.startsWith("boundary=")) boundary = p.substring(9).replace("\"","");
        }
        if (boundary == null || boundary.isBlank()) throw new IOException("Missing multipart boundary");
        byte[] all = ex.getRequestBody().readNBytes(maxBytes + 1);
        if (all.length > maxBytes) throw new IOException("Upload exceeds " + maxBytes + " bytes");
        String raw = new String(all, StandardCharsets.ISO_8859_1);
        String marker = "--" + boundary;
        Map<String,Part> parts = new LinkedHashMap<>();
        for (String block : raw.split(java.util.regex.Pattern.quote(marker))) {
            if (block.isBlank() || block.equals("--\r\n") || block.equals("--")) continue;
            if (block.startsWith("\r\n")) block = block.substring(2);
            if (block.endsWith("\r\n")) block = block.substring(0, block.length()-2);
            if (block.endsWith("--")) block = block.substring(0, block.length()-2);
            int split = block.indexOf("\r\n\r\n");
            if (split < 0) continue;
            String headers = block.substring(0, split);
            String content = block.substring(split+4);
            String name = null, filename = null, partCt = "application/octet-stream";
            for (String line : headers.split("\r\n")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-disposition:")) {
                    for (String token : line.split(";")) {
                        token = token.trim();
                        if (token.startsWith("name=")) name = token.substring(5).replace("\"","");
                        if (token.startsWith("filename=")) filename = token.substring(9).replace("\"","");
                    }
                } else if (lower.startsWith("content-type:")) {
                    partCt = line.substring(line.indexOf(':')+1).trim();
                }
            }
            if (name != null) parts.put(name, new Part(name, filename, partCt, content.getBytes(StandardCharsets.ISO_8859_1)));
        }
        return parts;
    }
}
