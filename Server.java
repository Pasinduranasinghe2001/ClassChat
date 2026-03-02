import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {
    private static final int PORT = 5000;
    // Map to store active clients (Username -> ClientHandler)
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    // History of the last 50 broadcast messages
    private static final ConcurrentLinkedQueue<String> messageHistory = new ConcurrentLinkedQueue<>();

    // GUI Components
    private static JFrame frame;
    private static JTextArea logArea;

    public static void main(String[] args) {
        // Build GUI on EDT
        SwingUtilities.invokeLater(Server::createAndShowGUI);

        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("Server started on port " + PORT + ". Waiting for clients...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientThread = new ClientHandler(clientSocket);
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame("ClassChat Server Monitor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 450);
        frame.setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(200, 200, 200));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Server Logs"));

        frame.add(scrollPane, BorderLayout.CENTER);

        // Bottom Panel for Controls
        JPanel controlPanel = new JPanel();
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JButton launchClientBtn = new JButton("Launch New Client");
        launchClientBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        launchClientBtn.addActionListener(e -> launchNewClient());
        controlPanel.add(launchClientBtn);
        frame.add(controlPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }

    private static void launchNewClient() {
        try {
            // Assume the user is running Windows, or handles java natively
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "javaw", "Client").start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("java", "Client").start();
            } else {
                new ProcessBuilder("java", "Client").start();
            }
            log("Launched a new Client instance.");
        } catch (IOException e) {
            log("Failed to launch new client: " + e.getMessage());
        }
    }

    /**
     * Logs messages to both the console and the GUI.
     */
    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = "[" + timestamp + "] " + message;
        System.out.println(formattedMessage);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(formattedMessage + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    /**
     * Adds a newly registered client to the active clients map.
     */
    public static synchronized boolean addClient(String username, ClientHandler handler) {
        if (!clients.containsKey(username)) {
            clients.put(username, handler);
            log("[+] New client connected: " + username);

            // Send chat history to the new client
            for (String pastMsg : messageHistory) {
                handler.sendMessage(pastMsg);
            }

            broadcastUserList();
            broadcastSystemMessage(username + " has joined the chat.");
            return true;
        }
        return false;
    }

    /**
     * Removes a disconnected client from the active clients map.
     */
    public static synchronized void removeClient(String username) {
        if (username != null) {
            // Check if client was actually in the map before broadcasting leave message
            if (clients.remove(username) != null) {
                log("[-] Client disconnected: " + username);
                broadcastUserList();
                broadcastSystemMessage(username + " has left the chat.");
            }
        }
    }

    /**
     * Routes a JSON message from the sender to the intended receiver.
     */
    public static void routeMessage(String sender, String receiver, String jsonMessage) {
        if ("All".equalsIgnoreCase(receiver)) {
            log("[Broadcast] " + sender + " to All.");

            // Save to history
            if (messageHistory.size() >= 50) {
                messageHistory.poll(); // Remove oldest
            }
            messageHistory.add(jsonMessage);

            for (ClientHandler handler : clients.values()) {
                if (!handler.getUsername().equals(sender)) {
                    handler.sendMessage(jsonMessage);
                }
            }
            return;
        }

        ClientHandler receiverHandler = clients.get(receiver);
        if (receiverHandler != null) {
            receiverHandler.sendMessage(jsonMessage);
            log("[Direct] " + sender + " -> " + receiver);
        } else {
            // Send back an error if the user is not found
            ClientHandler senderHandler = clients.get(sender);
            if (senderHandler != null) {
                String errorMsg = String.format(
                        "{\n  \"status\": \"0\",\n  \"sender\": \"Server\",\n  \"receiver\": \"%s\",\n  \"text\": \"Error: User %s is not currently online.\"\n}",
                        sender, receiver);
                senderHandler.sendMessage(errorMsg);
                log("[Error] " + sender + " tried to message missing user " + receiver);
            }
        }
    }

    /**
     * Broadcasts the current list of online users to all clients.
     */
    private static void broadcastUserList() {
        ArrayList<String> userList = new ArrayList<>(clients.keySet());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userList.size(); i++) {
            sb.append("\"").append(userList.get(i)).append("\"");
            if (i < userList.size() - 1)
                sb.append(",");
        }
        String usersJsonArray = "[" + sb.toString() + "]";

        String broadcastMsg = String.format(
                "{\n  \"status\": \"2\",\n  \"sender\": \"Server\",\n  \"receiver\": \"All\",\n  \"users\": %s\n}",
                usersJsonArray);

        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(broadcastMsg);
        }
    }

    /**
     * Broadcasts a system announcement to all clients.
     */
    private static void broadcastSystemMessage(String text) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String sysMsg = String.format(
                "{\n  \"status\": \"1\",\n  \"sender\": \"System\",\n  \"receiver\": \"All\",\n  \"text\": \"%s\",\n  \"timestamp\": \"%s\"\n}",
                text, timestamp);
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(sysMsg);
        }
    }
}
