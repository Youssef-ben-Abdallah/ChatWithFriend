package com.chatwithfriend;

import com.chatwithfriend.tcp.TcpChatLauncher;
import com.chatwithfriend.udp.UdpChatLauncher;
import java.util.Scanner;

public class AppMain {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to ChatWithFriend!");
        System.out.println("Choose a protocol to start chatting:");
        System.out.println("1) UDP");
        System.out.println("2) TCP");
        System.out.println("3) Exit");

        while (true) {
            System.out.print("Enter your choice (1-3): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    UdpChatLauncher.launch(scanner);
                    return;
                case "2":
                    TcpChatLauncher.launch(scanner);
                    return;
                case "3":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice. Please enter 1, 2, or 3.");
            }
        }
    }
}
