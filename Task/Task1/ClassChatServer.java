package Task.Task1;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClassChatServer {
    private static final int PORT = 5000;

    //  safer synchronized Set wrapper
    private static final Set<PrintWriter> clientWriters =
            Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("ClassChat Server starting on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening...");
            while (true) {
                Socket client = serverSocket.accept();
                new ClientHandler(client).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            setName("ClientHandler-" + socket.getInetAddress());
        }

        public void run() {
            try {
                System.out.println("New client connected: " + socket.getInetAddress());

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // ACK
                out.println("ACK: Connected to ClassChat Server.");

                clientWriters.add(out);

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                    broadcast(message);
                }

            } catch (IOException e) {
                System.out.println("Client disconnected or error: " + e.getMessage());
            } finally {
                clientWriters.remove(out);
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }
}