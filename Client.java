import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.*;

public class Client {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // GUI Components
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField messageField;
    private JComboBox<String> userDropdown;
    private DefaultComboBoxModel<String> userModel;
    private DefaultListModel<String> onlineUserModel;
    private JList<String> onlineUserList;

    public Client() {
        showLoginScreen();
    }

    private void showLoginScreen() {
        JFrame loginFrame = new JFrame("ClassChat");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 250);
        loginFrame.getContentPane().setBackground(new Color(30, 30, 30));
        loginFrame.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("ClassChat", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(new Color(0, 150, 255));

        JTextField usernameField = new JTextField(15);
        usernameField.setHorizontalAlignment(JTextField.CENTER);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        usernameField.setBackground(new Color(45, 45, 45));
        usernameField.setForeground(Color.WHITE);
        usernameField.setCaretColor(Color.WHITE);
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JButton loginButton = new JButton("Join Chat");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        loginButton.setBackground(new Color(0, 122, 255));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        panel.add(titleLabel, gbc);
        panel.add(usernameField, gbc);
        panel.add(loginButton, gbc);

        loginFrame.add(panel, BorderLayout.CENTER);

        ActionListener loginAction = e -> {
            String name = usernameField.getText().trim();
            if (!name.isEmpty()) {
                this.username = name;
                loginFrame.dispose();
                connectToServer();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Username cannot be empty");
            }
        };

        loginButton.addActionListener(loginAction);
        usernameField.addActionListener(loginAction);

        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String regJson = String.format(
                    "{\n  \"status\": \"1\",\n  \"sender\": \"%s\",\n  \"receiver\": \"Server\",\n  \"text\": \"Register\"\n}",
                    username);
            out.println(regJson);

            setupMainGUI();
            new Thread(new ServerListener()).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error connecting to server. Is it running?");
            System.exit(1);
        }
    }

    private void setupMainGUI() {
        frame = new JFrame("ClassChat - " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(25, 25, 25));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(35, 35, 35));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel headerLabel = new JLabel("ClassChat");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerLabel.setForeground(new Color(0, 150, 255));
        JLabel userLabel = new JLabel("Logged in as: " + username);
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userLabel.setForeground(Color.LIGHT_GRAY);
        headerPanel.add(headerLabel, BorderLayout.WEST);
        headerPanel.add(userLabel, BorderLayout.EAST);
        frame.add(headerPanel, BorderLayout.NORTH);

        // Chat Area
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(20, 20, 20));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(20);
        chatScroll.getVerticalScrollBar().setBackground(new Color(30, 30, 30));
        frame.add(chatScroll, BorderLayout.CENTER);

        // Sidebar
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setBackground(new Color(35, 35, 35));
        sidebarPanel.setPreferredSize(new Dimension(220, 0));
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 50, 50)));

        JLabel onlineLabel = new JLabel("Online Users");
        onlineLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        onlineLabel.setForeground(Color.WHITE);
        onlineLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        sidebarPanel.add(onlineLabel, BorderLayout.NORTH);

        onlineUserModel = new DefaultListModel<>();
        onlineUserList = new JList<>(onlineUserModel);
        onlineUserList.setBackground(new Color(35, 35, 35));
        onlineUserList.setForeground(Color.WHITE);
        onlineUserList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        onlineUserList.setFixedCellHeight(40);
        onlineUserList.setCellRenderer(new UserListCellRenderer());
        onlineUserList.setSelectionBackground(new Color(60, 60, 60));
        onlineUserList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUserList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onlineUserList.getSelectedValue() != null) {
                userDropdown.setSelectedItem(onlineUserList.getSelectedValue());
            }
        });

        JScrollPane userScroll = new JScrollPane(onlineUserList);
        userScroll.setBorder(null);
        sidebarPanel.add(userScroll, BorderLayout.CENTER);
        frame.add(sidebarPanel, BorderLayout.EAST);

        // Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBackground(new Color(35, 35, 35));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        userModel = new DefaultComboBoxModel<>();
        userModel.addElement("All");
        userDropdown = new JComboBox<>(userModel);
        userDropdown.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userDropdown.setBackground(new Color(50, 50, 50));
        userDropdown.setForeground(Color.WHITE);

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        messageField.setBackground(new Color(50, 50, 50));
        messageField.setForeground(Color.WHITE);
        messageField.setCaretColor(Color.WHITE);
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // Emoji Button
        JButton emojiButton = new JButton("😀");
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        emojiButton.setBackground(new Color(35, 35, 35));
        emojiButton.setForeground(Color.WHITE);
        emojiButton.setFocusPainted(false);
        emojiButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        emojiButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPopupMenu emojiPopup = new JPopupMenu();
        String[] emojis = { "😀", "😂", "🥺", "😡", "👍", "👎", "❤️", "🔥", "✨" };
        JPanel emojiPanel = new JPanel(new GridLayout(3, 3, 5, 5));
        emojiPanel.setBackground(new Color(40, 40, 40));
        emojiPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            btn.setBackground(new Color(50, 50, 50));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            btn.addActionListener(e -> {
                messageField.setText(messageField.getText() + emoji);
                emojiPopup.setVisible(false);
                messageField.requestFocus();
            });
            emojiPanel.add(btn);
        }
        emojiPopup.add(emojiPanel);

        emojiButton.addActionListener(e -> {
            emojiPopup.show(emojiButton, 0, -emojiPopup.getPreferredSize().height);
        });

        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sendButton.setBackground(new Color(0, 150, 60));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        messageField.addActionListener(sendAction);

        JPanel rightInputBox = new JPanel(new BorderLayout(5, 0));
        rightInputBox.setOpaque(false);
        rightInputBox.add(emojiButton, BorderLayout.WEST);
        rightInputBox.add(sendButton, BorderLayout.EAST);

        inputPanel.add(userDropdown, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(rightInputBox, BorderLayout.EAST);

        frame.add(inputPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        messageField.requestFocus();

        addBubble("System", "Connected to ClassChat Server!", null, false, true);
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty())
            return;

        String receiver = (String) userDropdown.getSelectedItem();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        addBubble("You" + ("All".equals(receiver) ? "" : " (to " + receiver + ")"), text, timestamp, true, false);

        String jsonMessage = String.format(
                "{\n  \"status\": \"1\",\n  \"sender\": \"%s\",\n  \"receiver\": \"%s\",\n  \"text\": \"%s\",\n  \"timestamp\": \"%s\"\n}",
                username, receiver, text, timestamp);
        out.println(jsonMessage);

        messageField.setText("");
        messageField.requestFocus();
    }

    private void addBubble(String sender, String text, String timestamp, boolean isSelf, boolean isSystem) {
        SwingUtilities.invokeLater(() -> {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setOpaque(false);

            if (!isSystem) {
                String bottomText = sender + (timestamp != null ? " • " + timestamp : "");
                if (isSelf)
                    bottomText += " ✓✓"; // Add double tick for read receipt simulation
                JLabel nameLabel = new JLabel(bottomText);
                nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                nameLabel.setForeground(isSelf ? new Color(120, 200, 120) : new Color(150, 180, 255));
                nameLabel.setAlignmentX(isSelf ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
                bubble.add(nameLabel);
                bubble.add(Box.createVerticalStrut(3));
            }

            JTextArea messageArea = new JTextArea(text);
            messageArea.setWrapStyleWord(true);
            messageArea.setLineWrap(true);
            messageArea.setEditable(false);

            // Emoji font fallback support
            messageArea.setFont(new Font("Segoe UI Emoji", isSystem ? Font.ITALIC : Font.PLAIN, 15));
            messageArea.setForeground(Color.WHITE);
            messageArea.setBackground(new Color(0, 0, 0, 0)); // Transparent initially
            messageArea.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

            // Limit width for long texts
            if (text.length() > 40) {
                messageArea.setColumns(40);
            }

            JPanel roundedPanel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (isSystem) {
                        g2.setColor(new Color(60, 60, 60));
                    } else if (isSelf) {
                        g2.setColor(new Color(0, 122, 255));
                    } else {
                        g2.setColor(new Color(65, 65, 65));
                    }
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            roundedPanel.setOpaque(false);
            roundedPanel.add(messageArea, BorderLayout.CENTER);
            roundedPanel.setAlignmentX(isSelf ? Component.RIGHT_ALIGNMENT
                    : (isSystem ? Component.CENTER_ALIGNMENT : Component.LEFT_ALIGNMENT));

            if (isSystem) {
                JLabel sysLabel = new JLabel(text + (timestamp != null ? " • " + timestamp : ""));
                sysLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                sysLabel.setForeground(new Color(150, 150, 150));
                roundedPanel = new JPanel(new BorderLayout());
                roundedPanel.setOpaque(false);
                roundedPanel.add(sysLabel, BorderLayout.CENTER);
                roundedPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
                bubble.add(roundedPanel);
            } else {
                bubble.add(roundedPanel);
            }

            if (isSystem) {
                wrapper.add(bubble, BorderLayout.CENTER);
            } else if (isSelf) {
                wrapper.add(bubble, BorderLayout.EAST);
            } else {
                wrapper.add(bubble, BorderLayout.WEST);
            }

            chatPanel.add(wrapper);
            chatPanel.add(Box.createVerticalStrut(5));
            chatPanel.revalidate();
            chatPanel.repaint();

            SwingUtilities.invokeLater(() -> {
                JScrollBar vBar = chatScroll.getVerticalScrollBar();
                vBar.setValue(vBar.getMaximum());
            });
        });
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
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

                    if (started && braceCount == 0) {
                        String fullJson = jsonBuilder.toString();
                        jsonBuilder.setLength(0);
                        started = false;

                        if (fullJson.contains("\"status\": \"2\"") || fullJson.contains("\"status\":\"2\"")) {
                            updateUserList(fullJson);
                        } else {
                            String sender = extractValue(fullJson, "sender");
                            String text = extractValue(fullJson, "text");
                            String receiver = extractValue(fullJson, "receiver");
                            String timestamp = extractValue(fullJson, "timestamp");

                            if (sender != null && text != null) {
                                if (sender.equals("Server") && text.startsWith("Error:")) {
                                    addBubble("Server", text, timestamp, false, true);
                                } else if (sender.equals("System")) {
                                    addBubble("System", text, timestamp, false, true);
                                } else if ("All".equalsIgnoreCase(receiver)) {
                                    addBubble(sender, text, timestamp, false, false);
                                } else {
                                    addBubble(sender + " (Private)", text, timestamp, false, false);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                addBubble("System", "Disconnected from server.", null, false, true);
            }
        }

        private String extractValue(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }

        private void updateUserList(String json) {
            Pattern p = Pattern.compile("\"users\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher m = p.matcher(json);
            if (m.find()) {
                String arrayContent = m.group(1);
                String[] users = arrayContent.split(",");

                SwingUtilities.invokeLater(() -> {
                    String currentSelection = (String) userDropdown.getSelectedItem();

                    onlineUserModel.clear();
                    userModel.removeAllElements();
                    userModel.addElement("All");

                    for (String u : users) {
                        String cleaned = u.replace("\"", "").trim();
                        if (!cleaned.isEmpty() && !cleaned.equals(username)) {
                            onlineUserModel.addElement(cleaned);
                            userModel.addElement(cleaned);
                        }
                    }

                    if (currentSelection != null) {
                        boolean found = false;
                        for (int i = 0; i < userModel.getSize(); i++) {
                            if (userModel.getElementAt(i).equals(currentSelection)) {
                                userDropdown.setSelectedIndex(i);
                                found = true;
                                break;
                            }
                        }
                        if (!found && userModel.getSize() > 0) {
                            userDropdown.setSelectedIndex(0);
                        }
                    }
                });
            }
        }
    }

    private class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 10));
            label.setIcon(createStatusIcon(new Color(0, 200, 80)));
            return label;
        }

        private Icon createStatusIcon(Color color) {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillOval(x, y + 2, 10, 10);
                    g2.dispose();
                }

                @Override
                public int getIconWidth() {
                    return 15;
                }

                @Override
                public int getIconHeight() {
                    return 15;
                }
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}
