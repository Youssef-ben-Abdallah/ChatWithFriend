# UDP Mode

UDP mode demonstrates low-latency, best-effort delivery. It is ideal for time-sensitive data such as live audio where occasional loss is acceptable.

## How it works
- Server listens on a UDP port.
- Clients send and receive datagrams without a persistent connection.
- Large payloads are chunked and reassembled.

## Strengths
- Low overhead and low latency.
- No connection setup required.
- Good fit for streaming audio or real-time updates.

## Trade-offs
- No built-in delivery guarantees.
- Packets may be lost, duplicated, or arrive out of order.
- Application must handle chunking and basic integrity checks.

## Typical usage
1. Start UDP server.
2. Connect one or more clients.
3. Send text, images, files, or voice.
4. Observe occasional gaps in audio or dropped chunks under loss.
