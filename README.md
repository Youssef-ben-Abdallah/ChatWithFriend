# SimpleNetworkingApp (TCP + UDP) — refactored

A Java Swing networking demo that shows **TCP vs UDP** with:
- Home screen (TCP / UDP)
- Start server, create multiple clients
- Text, file, image, and voice (PCM) messages
- Clean shutdown (Back arrow stops servers + clients)

## Requirements
- Java 17+ (works on Java 11+ if your Swing + sound support is fine)

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

## Notes
- TCP: reliable stream; binary is forwarded as raw bytes.
- UDP: best-effort; files/images are chunked + Base64 with basic loss detection; voice is streamed as UDP packets (may have gaps).
- Voice format: PCM 16kHz, 16-bit, mono.

## Dashboard
- Server screen shows connected users list and lets you kick a selected user.

## Voice UX
- Click mic to record, click again to stop.
- Click play to preview.
- Press send with empty text to send the recorded voice.

## If you see AbstractMethodError (VS Code)
This happens when VS Code runs old compiled classes.
- Delete `out/` (or `bin/`) then rebuild, OR run: **Java: Clean Java Language Server Workspace**.
