package za.co.focigroup.focitrace;

import com.sun.net.httpserver.HttpExchange;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final boolean secureCookie;

    public SessionManager(boolean secureCookie) { this.secureCookie = secureCookie; }

    public Session create(String username, String displayName, String role, boolean mustChangePassword) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String csrf = token();
        Session session = new Session(id, username, displayName, role, mustChangePassword, csrf, Instant.now());
        sessions.put(id, session);
        return session;
    }

    public Optional<Session> get(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return Optional.empty();
        for (String part : cookie.split(";")) {
            String[] kv = part.trim().split("=",2);
            if (kv.length==2 && kv[0].equals("FOCI_SESSION")) {
                Session s = sessions.get(kv[1]);
                if (s != null && s.createdAt().plusSeconds(8*60*60).isAfter(Instant.now())) return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public void attach(HttpExchange ex, Session s) {
        String value = "FOCI_SESSION=" + s.id() + "; Path=/; HttpOnly; SameSite=Lax" + (secureCookie ? "; Secure" : "");
        ex.getResponseHeaders().add("Set-Cookie", value);
    }

    public void destroy(HttpExchange ex) {
        get(ex).ifPresent(s -> sessions.remove(s.id()));
        ex.getResponseHeaders().add("Set-Cookie", "FOCI_SESSION=deleted; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (secureCookie?"; Secure":""));
    }

    public void markPasswordChanged(Session s) {
        sessions.put(s.id(), new Session(s.id(),s.username(),s.displayName(),s.role(),false,s.csrf(),s.createdAt()));
    }

    public boolean validCsrf(Session s, String token) { return token != null && token.equals(s.csrf()); }
    private String token() { byte[] b=new byte[24]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

    public record Session(String id, String username, String displayName, String role, boolean mustChangePassword, String csrf, Instant createdAt) {
        public boolean hasRole(String... roles) { return Arrays.stream(roles).anyMatch(role::equals); }
    }
}
