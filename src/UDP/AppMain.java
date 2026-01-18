package UDP;
// ==========================================================
// FILE: src/AppMain.java   (optional runner)
// PACKAGE: default (no package)
// ==========================================================
import UDP.Server.UdpChatServerUI;

public class AppMain {
    public static void main(String[] args) throws Exception {
        UdpChatServerUI.main(new String[]{"5000"});
    }
}
