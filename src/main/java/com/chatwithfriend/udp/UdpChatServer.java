package com.chatwithfriend.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UdpChatServer {
    private static final int BUFFER_SIZE = 1024;
    private final int port;
    private final Scanner scanner;

    public UdpChatServer(int port, Scanner scanner) {
        this.port = port;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("Starting UDP server on port " + port + "...");

        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("\nClient: " + message);

                if ("exit".equalsIgnoreCase(message)) {
                    System.out.println("Client ended the chat. Server shutting down.");
                    break;
                }

                System.out.print("Reply: ");
                String response = scanner.nextLine();
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                DatagramPacket responsePacket = new DatagramPacket(
                    responseBytes,
                    responseBytes.length,
                    clientAddress,
                    clientPort
                );
                socket.send(responsePacket);

                if ("exit".equalsIgnoreCase(response)) {
                    System.out.println("Server ended the chat.");
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("UDP server error: " + ex.getMessage());
        }
    }
}
