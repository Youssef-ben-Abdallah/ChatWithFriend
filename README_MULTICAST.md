# Multicast Mode

Multicast mode demonstrates one-to-many delivery on a shared group. It is useful for broadcast-style updates where multiple clients should receive the same message.

## How it works
- Clients join a multicast group address.
- Messages sent to the group are delivered to all members.
- The server may coordinate group membership or act as a relay.

## Strengths
- Efficient broadcast to many clients.
- Reduced network overhead compared to sending N unicast messages.

## Trade-offs
- Multicast routing may be limited to local networks.
- Delivery is best-effort (similar to UDP).
- Some networks block multicast traffic.

## Typical usage
1. Start multicast server (or group coordinator).
2. Join the multicast group from multiple clients.
3. Send text or media and observe one-to-many delivery.
4. Test on the same LAN for best results.
