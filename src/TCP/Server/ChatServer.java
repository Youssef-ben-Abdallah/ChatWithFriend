package TCP.Server;
import javax.swing.*;

import TCP.Client.ClientHandler;

import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 12345; // Changed from 123 to 12345 (common port)
    private static final Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();

    // GUI components
    private JFrame frame;
    private JTextArea textArea;
    private JLabel clientsLabel;

    public ChatServer() {
        setupGUI();
        startServer();
    }

    private void setupGUI() {
        frame = new JFrame("Java Chat Server");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        clientsLabel = new JLabel("Clients: None");
        frame.add(clientsLabel, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setEditable(false);
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                appendMessage("Server started on port " + PORT + ". Waiting for clients...");
                while (true) {
                    Socket socket = serverSocket.accept();
                    appendMessage("New client connected!");
                    ClientHandler client = new ClientHandler(socket, clientHandlers, this);
                    clientHandlers.add(client);
                    updateClientsLabel();
                    new Thread(client).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
                appendMessage("Server error: " + e.getMessage());
            }
        }).start();
    }

    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            // Auto-scroll to bottom
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public void updateClientsLabel() {
        SwingUtilities.invokeLater(() -> {
            if (clientHandlers.isEmpty()) {
                clientsLabel.setText("Clients: None");
            } else {
                StringBuilder sb = new StringBuilder("Clients: ");
                for (ClientHandler ch : clientHandlers) {
                    String n = ch.getClientName();
                    if (n != null) sb.append(n).append(", ");
                }
                if (sb.length() > 9) sb.setLength(sb.length() - 2);
                clientsLabel.setText(sb.toString());
            }
        });
        sendClientList();
    }

    public void sendClientList() {
        StringBuilder sb = new StringBuilder("USER_LIST:");
        for (ClientHandler ch : clientHandlers) {
            String n = ch.getClientName();
            if (n != null) sb.append(n).append(",");
        }
        if (sb.length() > "USER_LIST:".length()) sb.setLength(sb.length() - 1);
        String header = sb.toString();
        for (ClientHandler ch : clientHandlers) {
            ch.sendMessage(header);
        }
    }

    public void broadcast(String message, ClientHandler sender) {
        appendMessage(message);
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) ch.sendMessage(message);
        }
    }

    public void broadcastSystem(String systemMessage, ClientHandler exclude) {
        appendMessage(systemMessage);
        for (ClientHandler ch : clientHandlers) {
            if (ch != exclude) ch.sendMessage(systemMessage);
        }
    }

    public void sendPrivateMessage(String from, String to, String msg, ClientHandler sender) {
        boolean found = false;
        for (ClientHandler ch : clientHandlers) {
            if (to.equals(ch.getClientName())) {
                ch.sendMessage("(Private) " + from + ": " + msg);
                found = true;
                break;
            }
        }
        if (found) {
            appendMessage("(Private) " + from + " -> " + to + ": " + msg);
        } else {
            sender.sendMessage("⚠️ User '" + to + "' not found.");
        }
    }

    // IMAGE
    public void broadcastImageAll(String senderName, String filename, byte[] img, ClientHandler sender) {
        appendMessage(senderName + " sent an image to All: " + filename);
        String header = "IMG_ALL:" + senderName + ":" + filename + ":" + img.length;
        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) ch.sendImage(header, img);
        }
    }

    public void sendPrivateImage(String target, String senderName, String filename, byte[] img, ClientHandler sender) {
        boolean found = false;
        String header = "IMG_TO:" + target + ":" + senderName + ":" + filename + ":" + img.length;

        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendImage(header, img);
                found = true;
                break;
            }
        }
        if (found) {
            appendMessage(senderName + " sent a private image to " + target + ": " + filename);
        } else {
            sender.sendMessage("⚠️ User '" + target + "' not found.");
        }
    }

    // PDF
    public void broadcastPdfAll(String senderName, String filename, byte[] pdf, ClientHandler sender) {
        appendMessage(senderName + " sent a PDF to All: " + filename);
        String header = "PDF_ALL:" + senderName + ":" + filename + ":" + pdf.length;

        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) ch.sendFile(header, pdf);
        }
    }

    public void sendPrivatePdf(String target, String senderName, String filename, byte[] pdf, ClientHandler sender) {
        boolean found = false;
        String header = "PDF_TO:" + target + ":" + senderName + ":" + filename + ":" + pdf.length;

        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, pdf);
                found = true;
                break;
            }
        }
        if (found) {
            appendMessage(senderName + " sent a private PDF to " + target + ": " + filename);
        } else {
            sender.sendMessage("⚠️ User '" + target + "' not found.");
        }
    }

    // Generic FILE support
    public void broadcastFileAll(String senderName, String filename, byte[] fileData, ClientHandler sender) {
        appendMessage(senderName + " sent a file to All: " + filename);
        String header = "FILE_ALL:" + senderName + ":" + filename + ":" + fileData.length;

        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) ch.sendFile(header, fileData);
        }
    }

    public void sendPrivateFile(String target, String senderName, String filename, byte[] fileData, ClientHandler sender) {
        boolean found = false;
        String header = "FILE_TO:" + target + ":" + senderName + ":" + filename + ":" + fileData.length;

        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, fileData);
                found = true;
                break;
            }
        }
        if (found) {
            appendMessage(senderName + " sent a private file to " + target + ": " + filename);
        } else {
            sender.sendMessage("⚠️ User '" + target + "' not found.");
        }
    }

    // AUDIO support
    public void broadcastAudioAll(String senderName, String filename, byte[] audio, ClientHandler sender) {
        appendMessage(senderName + " sent a voice message to All: " + filename);
        String header = "AUDIO_ALL:" + senderName + ":" + filename + ":" + audio.length;

        for (ClientHandler ch : clientHandlers) {
            if (ch != sender) ch.sendFile(header, audio);
        }
    }

    public void sendPrivateAudio(String target, String senderName, String filename, byte[] audio, ClientHandler sender) {
        boolean found = false;
        String header = "AUDIO_TO:" + target + ":" + senderName + ":" + filename + ":" + audio.length;

        for (ClientHandler ch : clientHandlers) {
            if (target.equals(ch.getClientName())) {
                ch.sendFile(header, audio);
                found = true;
                break;
            }
        }
        if (found) {
            appendMessage(senderName + " sent a private voice message to " + target + ": " + filename);
        } else {
            sender.sendMessage("⚠️ User '" + target + "' not found.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServer());
    }
}