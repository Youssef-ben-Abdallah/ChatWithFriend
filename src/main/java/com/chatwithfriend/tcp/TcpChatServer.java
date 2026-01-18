package com.chatwithfriend.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TcpChatServer {
    private final int port;
    private final Scanner scanner;

    public TcpChatServer(int port, Scanner scanner) {
        this.port = port;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("Starting TCP server on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Waiting for a client to connect...");
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                System.out.println("Client connected: " + clientSocket.getInetAddress());

                while (true) {
                    String message = reader.readLine();
                    if (message == null) {
                        System.out.println("Client disconnected.");
                        break;
                    }

                    System.out.println("\nClient: " + message);

                    if ("exit".equalsIgnoreCase(message)) {
                        System.out.println("Client ended the chat. Server shutting down.");
                        break;
                    }

                    System.out.print("Reply: ");
                    String response = scanner.nextLine();
                    writer.println(response);

                    if ("exit".equalsIgnoreCase(response)) {
                        System.out.println("Server ended the chat.");
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("TCP server error: " + ex.getMessage());
        }
    }
}
