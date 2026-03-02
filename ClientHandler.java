import java.io.*;
import java.net.Socket;
import java.util.regex.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Auto-flush enabled for PrintWriter
            out = new PrintWriter(socket.getOutputStream(), true);

            // Wait for registration JSON message
            String regMessage = readJsonMessage();
            if (regMessage != null) {
                username = extractValue(regMessage, "sender");
                if (username != null && Server.addClient(username, this)) {

                    // Main message loop
                    String message;
                    while ((message = readJsonMessage()) != null) {
                        String sender = extractValue(message, "sender");
                        String receiver = extractValue(message, "receiver");

                        if (receiver != null && !receiver.isEmpty()) {
                            Server.routeMessage(sender, receiver, message);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Client interrupted/disconnected
        } finally {
            closeEverything();
        }
    }

    /**
     * Reads a full JSON message block from the client.
     */
    private String readJsonMessage() throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        int braceCount = 0;
        boolean started = false;

        while ((line = in.readLine()) != null) {
            jsonBuilder.append(line).append("\n");

            if (line.contains("{")) {
                braceCount++;
                started = true;
            }
            if (line.contains("}")) {
                braceCount--;
            }

            // Reached the end of the JSON object
            if (started && braceCount == 0) {
                return jsonBuilder.toString();
            }
        }
        return null; // Connection closed
    }

    /**
     * Simple regex-based extractor for JSON values to avoid external dependencies.
     */
    private String extractValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Sends a raw JSON string onto the client socket stream.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void closeEverything() {
        Server.removeClient(username);
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
