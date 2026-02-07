# TCP Mode

TCP mode demonstrates reliable, ordered delivery over a persistent connection. It is best when you need guaranteed delivery for files, images, or other critical payloads.

## How it works
- Server listens for client connections.
- Each client maintains a long-lived socket.
- Messages are delivered in order across the stream.

## Strengths
- Reliable delivery with built-in retransmission.
- Preserves ordering.
- Great for file transfers and consistent state updates.

## Trade-offs
- Higher latency due to acknowledgements and congestion control.
- Head-of-line blocking can delay subsequent messages if one packet is lost.

## Typical usage
1. Start TCP server.
2. Connect one or more clients.
3. Send text, images, files, or voice.
4. Observe that delivery is consistent even under moderate packet loss.
