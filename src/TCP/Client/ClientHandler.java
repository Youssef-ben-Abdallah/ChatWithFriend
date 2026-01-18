package TCP.Client;
import java.io.*;
import java.net.Socket;
import java.util.Set;

import TCP.Server.TcpChatServerCore;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Set<ClientHandler> clientHandlers;
    private String clientName;
    private final TcpChatServerCore serverCore;

    public ClientHandler(Socket socket, Set<ClientHandler> clientHandlers, TcpChatServerCore serverCore) throws IOException {
        this.socket = socket;
        this.clientHandlers = clientHandlers;
        this.serverCore = serverCore;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    @Override
    public void run() {
        try {
            String init = in.readUTF();
            if (init.startsWith("NAME:")) {
                clientName = init.substring("NAME:".length());
            } else {
                clientName = "Unknown";
            }

            if (serverCore != null) {
                serverCore.onClientConnected(clientName);
                serverCore.broadcastSystem(clientName + " joined the chat.", this);
            }

            while (true) {
                String header = in.readUTF();

                if (header.startsWith("MSG_ALL:")) {
                    String msg = header.substring("MSG_ALL:".length());
                    if (serverCore != null) serverCore.broadcast(clientName + ": " + msg, this);
                }
                else if (header.startsWith("MSG_TO:")) {
                    String[] parts = header.split(":", 3);
                    if (parts.length == 3 && serverCore != null) {
                        serverCore.sendPrivateMessage(clientName, parts[1], parts[2], this);
                    }
                }
                else if (header.startsWith("IMG_ALL:")) {
                    String[] parts = header.split(":", 4);
                    if (parts.length >= 4 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[3]);
                            byte[] img = new byte[size];
                            in.readFully(img);
                            serverCore.broadcastImageAll(parts[1], parts[2], img, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("IMG_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (parts.length >= 5 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[4]);
                            byte[] img = new byte[size];
                            in.readFully(img);
                            serverCore.sendPrivateImage(parts[1], parts[2], parts[3], img, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("PDF_ALL:")) {
                    String[] parts = header.split(":", 4);
                    if (parts.length >= 4 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[3]);
                            byte[] pdf = new byte[size];
                            in.readFully(pdf);
                            serverCore.broadcastPdfAll(parts[1], parts[2], pdf, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("PDF_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (parts.length >= 5 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[4]);
                            byte[] pdf = new byte[size];
                            in.readFully(pdf);
                            serverCore.sendPrivatePdf(parts[1], parts[2], parts[3], pdf, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("FILE_ALL:")) {
                    String[] parts = header.split(":", 4);
                    if (parts.length >= 4 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[3]);
                            byte[] fileData = new byte[size];
                            in.readFully(fileData);
                            serverCore.broadcastFileAll(parts[1], parts[2], fileData, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("FILE_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (parts.length >= 5 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[4]);
                            byte[] fileData = new byte[size];
                            in.readFully(fileData);
                            serverCore.sendPrivateFile(parts[1], parts[2], parts[3], fileData, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("AUDIO_ALL:")) {
                    String[] parts = header.split(":", 4);
                    if (parts.length >= 4 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[3]);
                            byte[] audio = new byte[size];
                            in.readFully(audio);
                            serverCore.broadcastAudioAll(parts[1], parts[2], audio, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
                else if (header.startsWith("AUDIO_TO:")) {
                    String[] parts = header.split(":", 5);
                    if (parts.length >= 5 && serverCore != null) {
                        try {
                            int size = Integer.parseInt(parts[4]);
                            byte[] audio = new byte[size];
                            in.readFully(audio);
                            serverCore.sendPrivateAudio(parts[1], parts[2], parts[3], audio, this);
                        } catch (NumberFormatException e) {
                            // Error logged by server core
                        }
                    }
                }
            }

        } catch (EOFException ignored) {
            // Connection closed normally
        } catch (IOException e) {
            // Connection error
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
            clientHandlers.remove(this);
            if (clientName != null && serverCore != null) {
                serverCore.onClientDisconnected(clientName);
                serverCore.broadcastSystem(clientName + " left the chat.", this);
            }
        }
    }

    public synchronized void sendMessage(String header) {
        try {
            out.writeUTF(header);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendImage(String header, byte[] img) {
        try {
            out.writeUTF(header);
            out.flush();
            out.write(img);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendFile(String header, byte[] data) {
        try {
            out.writeUTF(header);
            out.flush();
            out.write(data);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientName() {
        return clientName;
    }
    
    public Socket getSocket() {
        return socket;
    }
}