package Task.Task1;

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

    private Socket socket;              //  keep reference to close properly
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    // Theme colors (Light Green + Black)
    private static final Color BG = Color.BLACK;
    private static final Color FG = new Color(144, 238, 144);  // light green
    private static final Color BTN_BG = new Color(144, 238, 144);
    private static final Color BTN_FG = Color.BLACK;

    public ClassChatClient() {
        setupGUI();
    }

    private void setupGUI() {
        frame = new JFrame("ClassChat");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(520, 420);

        //  Close socket when window closes
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

        // Action Listeners
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            username = JOptionPane.showInputDialog(
                    frame,
                    "Enter your username:",
                    "Welcome to ClassChat",
                    JOptionPane.PLAIN_MESSAGE
            );

            if (username == null || username.trim().isEmpty()) {
                username = "Anonymous";
            }
            frame.setTitle("ClassChat - " + username);

            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Thread to listen for incoming messages
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        final String finalMsg = message;
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append(finalMsg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        });
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> chatArea.append("[System] Disconnected.\n"));
                } finally {
                    disconnect();
                }
            }, "ServerListener").start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Unable to connect to server: " + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
    }

    private void sendMessage() {
        String msgText = inputField.getText().trim();
        if (!msgText.isEmpty() && out != null) {
            out.println(username + ": " + msgText);
            inputField.setText("");
        }
    }

    private void disconnect() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClassChatClient client = new ClassChatClient();
            client.connectToServer();
        });
    }
}