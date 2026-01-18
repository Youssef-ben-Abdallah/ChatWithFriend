# ChatWithFriend

ChatWithFriend is a simple console-based chat application that lets you choose between UDP or TCP communication from a welcome screen.

## Project Structure

```
ChatWithFriend/
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── chatwithfriend/
                    ├── AppMain.java
                    ├── tcp/
                    │   ├── TcpChatClient.java
                    │   ├── TcpChatLauncher.java
                    │   └── TcpChatServer.java
                    └── udp/
                        ├── UdpChatClient.java
                        ├── UdpChatLauncher.java
                        └── UdpChatServer.java
```

## How It Works

1. Run `AppMain` to see the welcome screen.
2. Choose **UDP** or **TCP**.
3. Choose whether to start a server or a client.
4. Start typing messages. Use `exit` to close the chat.

## Running the App

Compile the Java sources:

```bash
cd /workspace/ChatWithFriend
javac -d out $(find src/main/java -name "*.java")
```

Start the app:

```bash
java -cp out com.chatwithfriend.AppMain
```

### Example: UDP Session

**Terminal 1 (Server)**

```bash
java -cp out com.chatwithfriend.AppMain
# choose UDP -> Server -> port 5000
```

**Terminal 2 (Client)**

```bash
java -cp out com.chatwithfriend.AppMain
# choose UDP -> Client -> host 127.0.0.1 -> port 5000
```

### Example: TCP Session

**Terminal 1 (Server)**

```bash
java -cp out com.chatwithfriend.AppMain
# choose TCP -> Server -> port 6000
```

**Terminal 2 (Client)**

```bash
java -cp out com.chatwithfriend.AppMain
# choose TCP -> Client -> host 127.0.0.1 -> port 6000
```

## Notes

- UDP is connectionless; messages are delivered in order in simple local tests but may not be reliable on wider networks.
- TCP is connection-oriented and ensures ordered delivery.
- Use `exit` on either side to close the chat gracefully.
