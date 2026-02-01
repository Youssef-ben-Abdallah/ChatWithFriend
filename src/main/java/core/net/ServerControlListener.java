package core.net;

import java.util.List;

/** Server -> UI notifications (connected clients list changes). */
public interface ServerControlListener {
    void onClientsChanged(List<String> clients);
}
