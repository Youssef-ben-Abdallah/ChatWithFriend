# ChatWithFriend — Simple Networking Demo (TCP + UDP + Multicast)

ChatWithFriend is a Java Swing networking demo that compares **TCP**, **UDP**, and **Multicast** behavior using a familiar chat-style UI. It includes a home screen for protocol selection, server/client management, and basic media messaging (text, file, image, and voice). Use it as a learning project or a quick reference when you want to see how transport choices affect delivery and UX.

## Features
- Home screen to choose **TCP**, **UDP**, or **Multicast** modes.
- Start server, create multiple clients.
- Text, file, image, and voice (PCM) messages.
- Clean shutdown (Back arrow stops servers + clients).

## Requirements
- Java 17+ (Java 11+ may work if your Swing + sound support is fine)

## Run (quick)
From the project root:

```bash
# Compile
javac -d out $(find src/main/java -name "*.java")

# Run
java -cp out app.Main
```

On Windows PowerShell:

```powershell
# Compile
mkdir out
Get-ChildItem -Recurse src\main\java\*.java | % { $_.FullName } | javac -d out -encoding UTF-8 @-

# Run
java -cp out app.Main
```

## Protocol notes
- **TCP**: Reliable byte stream; binary is forwarded as raw bytes.
- **UDP**: Best-effort; files/images are chunked + Base64 with basic loss detection; voice is streamed as UDP packets (may have gaps).
- **Multicast**: One-to-many delivery; clients join a group and receive messages from peers on the same group.

## Dashboard
- Server screen shows connected users list and lets you kick a selected user.

## Voice UX
- Click mic to record, click again to stop.
- Click play to preview.
- Press send with empty text to send the recorded voice.

## Troubleshooting
If you see `AbstractMethodError` in VS Code, it usually means old compiled classes are being used.
- Delete `out/` (or `bin/`) then rebuild, OR run: **Java: Clean Java Language Server Workspace**.
