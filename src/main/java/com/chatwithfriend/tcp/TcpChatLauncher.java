package com.chatwithfriend.tcp;

import java.util.Scanner;

public class TcpChatLauncher {
    public static void launch(Scanner scanner) {
        System.out.println("\nTCP Chat");
        System.out.println("1) Start TCP Server");
        System.out.println("2) Start TCP Client");

        while (true) {
            System.out.print("Enter your choice (1-2): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    startServer(scanner);
                    return;
                case "2":
                    startClient(scanner);
                    return;
                default:
                    System.out.println("Invalid choice. Please enter 1 or 2.");
            }
        }
    }

    private static void startServer(Scanner scanner) {
        int port = promptForPort(scanner, "Enter port for TCP server (e.g., 6000): ");
        TcpChatServer server = new TcpChatServer(port, scanner);
        server.start();
    }

    private static void startClient(Scanner scanner) {
        System.out.print("Enter server host (e.g., 127.0.0.1): ");
        String host = scanner.nextLine().trim();
        int port = promptForPort(scanner, "Enter server port (e.g., 6000): ");
        TcpChatClient client = new TcpChatClient(host, port, scanner);
        client.start();
    }

    private static int promptForPort(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int port = Integer.parseInt(input);
                if (port > 0 && port <= 65535) {
                    return port;
                }
                System.out.println("Port must be between 1 and 65535.");
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
