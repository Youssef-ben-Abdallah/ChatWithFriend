package core.net;

import java.util.List;

/**
 * A server API that the UI can control:
 * - read connected clients
 * - kick a user
 * - receive client-list updates
 */
public interface ServerControlApi extends ServerApi {
    List<String> getClients();
    void kick(String name, String reason);

    void setListener(ServerControlListener listener);
}
