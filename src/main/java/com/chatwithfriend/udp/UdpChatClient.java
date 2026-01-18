package com.chatwithfriend.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpChatClient {
    private static final int BUFFER_SIZE = 1024;
    private final String host;
    private final int port;
    private final Scanner scanner;

    public UdpChatClient(String host, int port, Scanner scanner) {
        this.host = host;
        this.port = port;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("Starting UDP client for " + host + ":" + port + "...");

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(host);

            while (true) {
                System.out.print("You: ");
                String message = scanner.nextLine();
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                DatagramPacket packet = new DatagramPacket(
                    messageBytes,
                    messageBytes.length,
                    serverAddress,
                    port
                );
                socket.send(packet);

                if ("exit".equalsIgnoreCase(message)) {
                    System.out.println("You ended the chat.");
                    break;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(responsePacket);

                String response = new String(
                    responsePacket.getData(),
                    0,
                    responsePacket.getLength(),
                    StandardCharsets.UTF_8
                );
                System.out.println("Server: " + response);

                if ("exit".equalsIgnoreCase(response)) {
                    System.out.println("Server ended the chat.");
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("UDP client error: " + ex.getMessage());
        }
    }
}
