package UDP.Server;

import java.net.SocketAddress;

class UdpClientHandler {
    private final UdpChatServerCore serverCore;
    private final String clientName;
    private final SocketAddress address;

    UdpClientHandler(UdpChatServerCore serverCore, String clientName, SocketAddress address) {
        this.serverCore = serverCore;
        this.clientName = clientName;
        this.address = address;
    }

    String getClientName() {
        return clientName;
    }

    SocketAddress getAddress() {
        return address;
    }

    void send(String type, String from, String to, String payload) {
        String message = type + "|" + from + "|" + to + "|" + payload;
        serverCore.sendRaw(message, address);
    }
}
