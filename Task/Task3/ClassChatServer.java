package Task.Task3;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassChatServer {
    private static final int PORT = 5000;

    // username -> writer
    private static final Map<String, PrintWriter> users = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("ClassChat Server (Task3) starting on port " + PORT + "...");
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
        private String myUser = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
            setName("ClientHandler-" + socket.getInetAddress());
        }

        @Override
        public void run() {
            try {
                System.out.println("Client connected: " + socket.getInetAddress());

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // ACK
                out.println(buildJson("info", "server", "", "ACK: Connected to ClassChat Server (Task3)."));

                String line;
                while ((line = in.readLine()) != null) {
                    String status = getJsonValue(line, "status");

                    if ("join".equalsIgnoreCase(status)) {
                        String sender = getJsonValue(line, "sender");
                        myUser = sender;
                        users.put(sender, out);
                        System.out.println(sender + " joined.");
                        out.println(buildJson("info", "server", sender, "Welcome " + sender + "!"));
                        continue;
                    }

                    if ("leave".equalsIgnoreCase(status)) {
                        String sender = getJsonValue(line, "sender");
                        users.remove(sender);
                        System.out.println(sender + " left.");
                        break;
                    }

                    if ("msg".equalsIgnoreCase(status)) {
                        String sender = getJsonValue(line, "sender");
                        String receiver = getJsonValue(line, "receiver");
                        String text = getJsonValue(line, "text");

                        // receiver exists?
                        PrintWriter recvOut = users.get(receiver);
                        if (recvOut == null) {
                            // send error back to sender
                            out.println(buildJson("error", "server", sender,
                                    "User '" + receiver + "' is not connected."));
                        } else {
                            // forward message to receiver
                            recvOut.println(buildJson("deliver", sender, receiver, text));
                            // optional: confirm to sender
                            out.println(buildJson("info", "server", sender,
                                    "Delivered to " + receiver));
                        }
                        continue;
                    }

                    // Unknown status
                    out.println(buildJson("error", "server", "", "Unknown message format/status."));
                }

            } catch (IOException e) {
                System.out.println("Client disconnected/error: " + e.getMessage());
            } finally {
                if (myUser != null) users.remove(myUser);
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    // -------- JSON helpers (simple flat JSON only) --------

    private static String buildJson(String status, String sender, String receiver, String text) {
        return "{"
                + "\"status\":\"" + escape(status) + "\","
                + "\"sender\":\"" + escape(sender) + "\","
                + "\"receiver\":\"" + escape(receiver) + "\","
                + "\"text\":\"" + escape(text) + "\""
                + "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String getJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"";
            int i = json.indexOf(pattern);
            if (i < 0) return "";
            int colon = json.indexOf(":", i);
            int firstQuote = json.indexOf("\"", colon + 1);
            int secondQuote = json.indexOf("\"", firstQuote + 1);
            while (secondQuote > 0 && json.charAt(secondQuote - 1) == '\\') {
                secondQuote = json.indexOf("\"", secondQuote + 1);
            }
            if (firstQuote < 0 || secondQuote < 0) return "";
            String raw = json.substring(firstQuote + 1, secondQuote);
            return raw.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) {
            return "";
        }
    }
}