package com.chatwithfriend.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TcpChatClient {
    private final String host;
    private final int port;
    private final Scanner scanner;

    public TcpChatClient(String host, int port, Scanner scanner) {
        this.host = host;
        this.port = port;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("Starting TCP client for " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            while (true) {
                System.out.print("You: ");
                String message = scanner.nextLine();
                writer.println(message);

                if ("exit".equalsIgnoreCase(message)) {
                    System.out.println("You ended the chat.");
                    break;
                }

                String response = reader.readLine();
                if (response == null) {
                    System.out.println("Server disconnected.");
                    break;
                }

                System.out.println("Server: " + response);

                if ("exit".equalsIgnoreCase(response)) {
                    System.out.println("Server ended the chat.");
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("TCP client error: " + ex.getMessage());
        }
    }
}
