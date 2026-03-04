package Task.Task3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

public class ClassChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String username;
    private String receiver;

    // Theme colors (Light Green + Black)
    private static final Color BG = Color.BLACK;
    private static final Color FG = new Color(144, 238, 144);
    private static final Color BTN_BG = new Color(144, 238, 144);
    private static final Color BTN_FG = Color.BLACK;

    public ClassChatClient() {
        setupGUI();
    }

    private void setupGUI() {
        frame = new JFrame("ClassChat (Task3)");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(560, 440);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                frame.dispose();
                System.exit(0);
            }
        });

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        chatArea.setBackground(BG);
        chatArea.setForeground(FG);
        chatArea.setCaretColor(FG);

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.getViewport().setBackground(BG);
        scrollPane.setBorder(BorderFactory.createLineBorder(FG, 1));

        inputField = new JTextField();
        inputField.setFont(new Font("Consolas", Font.PLAIN, 14));
        inputField.setBackground(BG);
        inputField.setForeground(FG);
        inputField.setCaretColor(FG);
        inputField.setBorder(BorderFactory.createLineBorder(FG, 1));

        sendButton = new JButton("Send");
        sendButton.setBackground(BTN_BG);
        sendButton.setForeground(BTN_FG);
        sendButton.setFocusPainted(false);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBackground(BG);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BG);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            username = askNonEmpty("Enter your username:");
            receiver = askNonEmpty("Enter receiver username (who you want to chat with):");

            frame.setTitle("ClassChat - " + username + " → " + receiver + " (Task3)");

            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Join (JSON)
            out.println(buildJson("join", username, "", "joined"));

            // Receive thread
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String pretty = prettyPrintJson(line);
                        appendLine(pretty);
                    }
                } catch (IOException e) {
                    appendLine("[System] Disconnected.");
                } finally {
                    disconnect();
                }
            }, "ServerListener").start();

            appendLine("[System] Connected. Chatting with: " + receiver);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                    "Unable to connect to server: " + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || out == null) return;

        // Send JSON message to server
        out.println(buildJson("msg", username, receiver, text));

        // Show locally too
        appendLine("[You → " + receiver + "] " + text);

        inputField.setText("");
    }

    private void disconnect() {
        try {
            if (out != null && username != null) {
                out.println(buildJson("leave", username, "", "left"));
            }
        } catch (Exception ignored) {}

        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
    }

    // ---------------- helpers ----------------

    private void appendLine(String s) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(s + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private String askNonEmpty(String prompt) {
        while (true) {
            String s = JOptionPane.showInputDialog(frame, prompt, "ClassChat (Task3)", JOptionPane.PLAIN_MESSAGE);
            if (s == null) return "Anonymous";
            s = s.trim();
            if (!s.isEmpty()) return s;
        }
    }

    // Build minimal JSON (no external libraries)
    private String buildJson(String status, String sender, String receiver, String text) {
        return "{"
                + "\"status\":\"" + escape(status) + "\","
                + "\"sender\":\"" + escape(sender) + "\","
                + "\"receiver\":\"" + escape(receiver) + "\","
                + "\"text\":\"" + escape(text) + "\""
                + "}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // Make received JSON readable in chat area
    private String prettyPrintJson(String json) {
        String status = getJsonValue(json, "status");
        String sender = getJsonValue(json, "sender");
        String receiverVal = getJsonValue(json, "receiver");
        String text = getJsonValue(json, "text");

        if ("deliver".equalsIgnoreCase(status)) {
            return "[" + sender + " → You] " + text;
        }
        if ("error".equalsIgnoreCase(status)) {
            return "[Error] " + text;
        }
        if ("info".equalsIgnoreCase(status)) {
            return "[System] " + text;
        }
        return "[Server] " + json; // fallback
    }

    // Very small JSON value extractor for simple flat JSON objects
    private String getJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"";
            int i = json.indexOf(pattern);
            if (i < 0) return "";
            int colon = json.indexOf(":", i);
            int firstQuote = json.indexOf("\"", colon + 1);
            int secondQuote = json.indexOf("\"", firstQuote + 1);
            while (secondQuote > 0 && json.charAt(secondQuote - 1) == '\\') { // handle escaped quotes
                secondQuote = json.indexOf("\"", secondQuote + 1);
            }
            if (firstQuote < 0 || secondQuote < 0) return "";
            String raw = json.substring(firstQuote + 1, secondQuote);
            return raw.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) {
            return "";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClassChatClient client = new ClassChatClient();
            client.connectToServer();
        });
    }
}