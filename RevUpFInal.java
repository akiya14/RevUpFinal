import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;

import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Class representing an inventory item
class InventoryItem {
    String id, name, category;
    int quantity;
    double price;

    public InventoryItem(String id, String name, int quantity, double price, String category) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.category = category;
    }
}

// Manages all database interactions
class DatabaseManager {
    private Connection conn;

    public DatabaseManager() {
        try {
            // Establish connection to SQLite database
            conn = DriverManager.getConnection("jdbc:sqlite:RevUp.db");
            Statement stmt = conn.createStatement();
            // Create items table if it doesn't exist
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, quantity INTEGER, price REAL, category TEXT)");
            // Create sales table if it doesn't exist
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS sales (sale_id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT, quantity_sold INTEGER, price_sold REAL, date TEXT)");
            // Create users table if it doesn't exist
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT, role TEXT)");
            // Insert default users if they don't already exist
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('admin', 'admin123', 'admin')");
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('staff', 'staff123', 'staff')");
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('viewer', 'viewer123', 'viewer')");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Adds a new item to the database
    public void addItem(InventoryItem item) {
        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO items VALUES (?, ?, ?, ?, ?)");
            stmt.setString(1, item.id);
            stmt.setString(2, item.name);
            stmt.setInt(3, item.quantity);
            stmt.setDouble(4, item.price);
            stmt.setString(5, item.category);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Show error if ID already exists (primary key constraint violation)
            JOptionPane.showMessageDialog(null, "ID already exists.");
        }
    }

    // Updates an existing item in the database
    public void updateItem(InventoryItem item) {
        try {
            PreparedStatement stmt = conn
                    .prepareStatement("UPDATE items SET name=?, quantity=?, price=?, category=? WHERE id=?");
            stmt.setString(1, item.name);
            stmt.setInt(2, item.quantity);
            stmt.setDouble(3, item.price);
            stmt.setString(4, item.category);
            stmt.setString(5, item.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Deletes an item from the database by ID
    public void deleteItem(String id) {
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM items WHERE id=?");
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Retrieves all items from the database
    public ResultSet getAllItems() {
        try {
            Statement stmt = conn.createStatement();
            return stmt.executeQuery("SELECT * FROM items");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Searches for items by ID or name
    public ResultSet searchItems(String keyword) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM items WHERE id LIKE ? OR name LIKE ?");
            stmt.setString(1, "%" + keyword + "%");
            stmt.setString(2, "%" + keyword + "%");
            return stmt.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Records a sale and updates item quantity
    public void recordSale(String itemId, int quantitySold, double priceSold, String date) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sales (item_id, quantity_sold, price_sold, date) VALUES (?, ?, ?, ?)");
            stmt.setString(1, itemId);
            stmt.setInt(2, quantitySold);
            stmt.setDouble(3, priceSold);
            stmt.setString(4, date);
            stmt.executeUpdate();

            // Update the quantity of the sold item
            PreparedStatement update = conn.prepareStatement("UPDATE items SET quantity = quantity - ? WHERE id = ?");
            update.setInt(1, quantitySold);
            update.setString(2, itemId);
            update.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Calculates and returns the total revenue from sales
    public double getTotalRevenue() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT SUM(quantity_sold * price_sold) AS total FROM sales");
            return rs.next() ? rs.getDouble("total") : 0.0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Retrieves aggregated monthly revenue summary for a specific year.
     * 
     * @param year The year to filter by. If 0, returns all years.
     * @return ResultSet containing month_year (YYYY-MM) and total_revenue.
     */
    public ResultSet getMonthlyRevenueSummary(int year) {
        try {
            String query;
            if (year > 0) {
                query = "SELECT strftime('%Y-%m', date) AS month_year, SUM(quantity_sold * price_sold) AS total_revenue "
                        +
                        "FROM sales WHERE strftime('%Y', date) = ? GROUP BY month_year ORDER BY month_year DESC";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setString(1, String.valueOf(year));
                return pstmt.executeQuery();
            } else {
                query = "SELECT strftime('%Y-%m', date) AS month_year, SUM(quantity_sold * price_sold) AS total_revenue "
                        +
                        "FROM sales GROUP BY month_year ORDER BY month_year DESC";
                Statement stmt2 = conn.createStatement(); // Use a different variable name to avoid duplicate
                return stmt2.executeQuery(query);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Calculates the total annual revenue for a specific year.
     * 
     * @param year The year to calculate revenue for.
     * @return Total annual revenue.
     */
    public double getTotalAnnualRevenue(int year) {
        try {
            String query = "SELECT SUM(quantity_sold * price_sold) AS total FROM sales WHERE strftime('%Y', date) = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, String.valueOf(year));
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getDouble("total") : 0.0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    /**
     * Retrieves individual sales data for a specific month and year, including item
     * name.
     * 
     * @param yearMonth The month and year in YYYY-MM format.
     * @return ResultSet containing individual sales data or null if an error
     *         occurs.
     */
    public ResultSet getIndividualSalesForMonth(String yearMonth) {
        try {
            String query = "SELECT s.sale_id, s.item_id, i.name AS item_name, s.quantity_sold, s.price_sold, s.date " +
                    "FROM sales s JOIN items i ON s.item_id = i.id " +
                    "WHERE strftime('%Y-%m', s.date) = ? ORDER BY s.date ASC";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, yearMonth);
            return stmt.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Resets all sales data
    public void resetRevenue() {
        try {
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM sales");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Authenticates a user and returns their role if successful
    public String authenticate(String username, String password) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT role FROM users WHERE username=? AND password=?");
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("role") : null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Deletes a sale from the sales table by sale ID
    public void deleteSale(int saleId) {
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM sales WHERE sale_id=?");
            stmt.setInt(1, saleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

// Custom Login Frame
class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel errorMessageLabel;
    private DatabaseManager db;

    public LoginFrame(DatabaseManager db) {
        this.db = db;
        setTitle("Login to RevUp Inventory System");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center the window

        // Set custom look and feel for a modern touch
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore if L&F cannot be set
        }

        // Define colors
        Color backgroundColor = new Color(240, 242, 245); // Light grey background
        Color accentColor = new Color(24, 119, 242); // Facebook blue-like
        Color buttonTextColor = Color.WHITE;
        Color inputBorderColor = new Color(200, 200, 200); // Light grey border for inputs
        Color errorColor = new Color(220, 20, 60); // Crimson red for errors

        // Main panel for content
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(backgroundColor);
        mainPanel.setBorder(new EmptyBorder(20, 30, 20, 30)); // Padding around the content

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0); // Padding between components
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Login Title
        JLabel titleLabel = new JLabel("Login");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28)); // Modern, bold font
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Span across two columns
        gbc.anchor = GridBagConstraints.CENTER; // Center horizontally
        mainPanel.add(titleLabel, gbc);

        // Username Field
        usernameField = new JTextField(20);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        usernameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(inputBorderColor, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15))); // Padding inside
        usernameField.putClientProperty("JTextField.variant", "search"); // Hint for some L&F
        // Placeholder text
        setPlaceholder(usernameField, "Username");

        gbc.gridy = 1;
        gbc.gridwidth = 2;
        mainPanel.add(usernameField, gbc);

        // Password Field
        passwordField = new JPasswordField(20);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(inputBorderColor, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15))); // Padding inside
        // Placeholder text
        setPlaceholder(passwordField, "Password");

        gbc.gridy = 2;
        mainPanel.add(passwordField, gbc);

        // Error Message Label
        errorMessageLabel = new JLabel("");
        errorMessageLabel.setForeground(errorColor);
        errorMessageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 5, 0); // Smaller top padding
        mainPanel.add(errorMessageLabel, gbc);

        // Login Button
        loginButton = new JButton("Login");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginButton.setBackground(accentColor);
        loginButton.setForeground(buttonTextColor);
        loginButton.setBorderPainted(false); // No default border
        loginButton.setFocusPainted(false); // No focus border on click
        loginButton.setPreferredSize(new Dimension(100, 45)); // Fixed size for consistent look
        loginButton.setMinimumSize(new Dimension(100, 45));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Hand cursor on hover

        // Make button full width
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 0, 10, 0); // Padding around button
        mainPanel.add(loginButton, gbc);

        // Placeholder for "Perserwore:" checkbox - since the image doesn't show a clear
        // label for it,
        // and its a Swing app, I'll add a simple "Remember Me" checkbox as a common
        // login feature.
        JCheckBox rememberMe = new JCheckBox("Remember Me");
        rememberMe.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        rememberMe.setBackground(backgroundColor);
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST; // Align to the left
        mainPanel.add(rememberMe, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // Add action listener to login button
        loginButton.addActionListener(_ -> attemptLogin());

        // Allow pressing Enter key to login
        getRootPane().setDefaultButton(loginButton);
    }

    // Helper method to set placeholder text for JTextFields
    private void setPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(Color.GRAY);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(Color.GRAY);
                }
            }
        });
    }

    // Helper method to set placeholder text for JPasswordFields
    private void setPlaceholder(JPasswordField field, String placeholder) {
        field.setEchoChar((char) 0); // Show characters for placeholder
        field.setText(placeholder);
        field.setForeground(Color.GRAY);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (new String(field.getPassword()).equals(placeholder)) {
                    field.setText("");
                    field.setEchoChar('*'); // Hide characters for password
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (new String(field.getPassword()).isEmpty()) {
                    field.setEchoChar((char) 0); // Show characters for placeholder
                    field.setText(placeholder);
                    field.setForeground(Color.GRAY);
                }
            }
        });
    }

    private void attemptLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        // Clear placeholder if it's still there before authenticating
        if (username.equals("Username")) {
            username = "";
        }
        if (password.equals("Password")) { // Check against placeholder string
            password = "";
        }

        String role = db.authenticate(username, password);

        if (role != null) {
            errorMessageLabel.setText(""); // Clear any previous error message
            dispose(); // Close login window
            new RevUpApp(username, role, db).setVisible(true); // Open main app window
        } else {
            errorMessageLabel.setText("Invalid username or password. Please try again.");
            passwordField.setText(""); // Clear password field
            usernameField.requestFocusInWindow(); // Focus back to username
        }
    }
}

// Main application window for the inventory system
public class RevUpApp extends JFrame {
    private JTextField idField, nameField, quantityField, priceField, searchField, sellQtyField;
    private JComboBox<String> categoryBox, filterCategoryBox;
    private DefaultTableModel tableModel; // Inventory table model
    private JTable inventoryTable; // Renamed for clarity
    private JLabel revenueLabel;
    private DatabaseManager db;
    private String currentUser;
    private String currentRole;

    // Sales Report Components
    private JTable monthlyRevenueTable; // New table for monthly summaries
    private DefaultTableModel monthlyRevenueTableModel; // Model for monthly summaries
    private JComboBox<String> yearFilterComboBox; // New: Year filter for monthly revenue
    private JLabel annualRevenueLabel; // New: Label to display total annual revenue

    // Define the low stock threshold for visual indication
    private static final int LOW_STOCK_THRESHOLD = 5; // Items with quantity <= 5 will be considered low stock

    public RevUpApp(String username, String role, DatabaseManager dbManager) {
        this.db = dbManager;
        this.currentUser = username;
        this.currentRole = role;

        setTitle("RevUp Inventory System - Logged in as: " + currentUser + " [" + currentRole + "]");
        setSize(1100, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null); // Center the main app window

        // Define color scheme (consistent with LoginFrame)
        Color primaryColor = new Color(33, 47, 61); // Dark blue/grey
        Color accentColor = new Color(24, 119, 242); // Facebook blue-like for buttons
        Color lightAccentColor = new Color(41, 128, 185); // Bright blue for table header (kept previous)
        Color textColor = Color.WHITE;
        Color lightGreyBackground = new Color(240, 242, 245); // Light grey background for overall frame
        Color evenRowColor = new Color(248, 248, 248); // Very light grey for even rows
        Color oddRowColor = Color.WHITE; // White for odd rows

        // Set the background of the content pane to light grey
        getContentPane().setBackground(lightGreyBackground);

        // Main Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Style for tab titles

        // -------------------- Inventory Management Tab --------------------
        JPanel inventoryPanel = new JPanel(new BorderLayout());
        inventoryPanel.setBackground(lightGreyBackground);

        // Input panel for item details and search/filter fields
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(new EmptyBorder(15, 15, 15, 15)); // Add more padding
        inputPanel.setBackground(primaryColor); // Dark background for input section

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10); // Padding around each component
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Allow components to grow horizontally

        // Row 1: Labels
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST; // Align labels to the left
        inputPanel.add(createStyledLabel("ID"), gbc(0, 0, gbc));
        inputPanel.add(createStyledLabel("Name"), gbc(1, 0, gbc));
        inputPanel.add(createStyledLabel("Quantity"), gbc(2, 0, gbc));
        inputPanel.add(createStyledLabel("Price"), gbc(3, 0, gbc));
        inputPanel.add(createStyledLabel("Category"), gbc(4, 0, gbc));
        inputPanel.add(createStyledLabel("Search"), gbc(5, 0, gbc));
        inputPanel.add(createStyledLabel("Sell Qty"), gbc(6, 0, gbc));
        inputPanel.add(createStyledLabel("Filter Category"), gbc(7, 0, gbc));

        // Row 2: Input fields
        gbc.gridy = 1;
        idField = createStyledTextField();
        nameField = createStyledTextField();
        quantityField = createStyledTextField();
        priceField = createStyledTextField();
        categoryBox = createStyledComboBox(new String[] { "Electronics", "Clothing", "Furniture", "Other" });
        searchField = createStyledTextField();
        sellQtyField = createStyledTextField();
        filterCategoryBox = createStyledComboBox(
                new String[] { "All", "Electronics", "Clothing", "Furniture", "Other" });

        inputPanel.add(idField, gbc(0, 1, gbc));
        inputPanel.add(nameField, gbc(1, 1, gbc));
        inputPanel.add(quantityField, gbc(2, 1, gbc));
        inputPanel.add(priceField, gbc(3, 1, gbc));
        inputPanel.add(categoryBox, gbc(4, 1, gbc));
        inputPanel.add(searchField, gbc(5, 1, gbc));
        inputPanel.add(sellQtyField, gbc(6, 1, gbc));
        inputPanel.add(filterCategoryBox, gbc(7, 1, gbc));

        inventoryPanel.add(inputPanel, BorderLayout.NORTH);

        // Table for displaying inventory items
        tableModel = new DefaultTableModel(new String[] { "ID", "Name", "Quantity", "Price", "Category" }, 0);
        inventoryTable = new JTable(tableModel); // Renamed
        TableRowSorter<TableModel> inventorySorter = new TableRowSorter<>(tableModel);
        inventoryTable.setRowSorter(inventorySorter);
        // Set default sort order for quantity and price
        inventorySorter.setSortKeys(List.of(
                new RowSorter.SortKey(2, SortOrder.ASCENDING),
                new RowSorter.SortKey(3, SortOrder.ASCENDING)));
        inventoryTable.setRowHeight(30); // Increase row height for better readability
        inventoryTable.setSelectionBackground(new Color(174, 214, 241)); // Light blue selection background
        inventoryTable.setFont(new Font("Segoe UI", Font.PLAIN, 14)); // Consistent font for table data

        // Custom renderer for inventory table header
        DefaultTableCellRenderer inventoryHeaderRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(JLabel.CENTER);
                ((JComponent) c).setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(150, 150, 150)), // Bottom and right
                                                                                               // border
                        BorderFactory.createEmptyBorder(8, 5, 8, 5))); // Padding
                return c;
            }
        };
        inventoryTable.getTableHeader().setBackground(lightAccentColor); // Consistent lighter blue
        inventoryTable.getTableHeader().setForeground(Color.BLACK);
        inventoryTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 16));
        inventoryTable.getTableHeader().setDefaultRenderer(inventoryHeaderRenderer);

        // Wrap inventory table in JScrollPane
        JScrollPane inventoryScrollPane = new JScrollPane(inventoryTable); // Renamed
        inventoryScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15)); // Padding around the table
        inventoryScrollPane.setBackground(lightGreyBackground); // Match overall background
        inventoryPanel.add(inventoryScrollPane, BorderLayout.CENTER);

        // Apply custom cell renderer for ALL columns to handle striped rows and general
        // cell styling for Inventory Table
        DefaultTableCellRenderer inventoryCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                        column);

                // Apply striped row background
                if (!isSelected) { // Only apply striped background if not selected
                    label.setBackground(row % 2 == 0 ? evenRowColor : oddRowColor);
                } else {
                    label.setBackground(table.getSelectionBackground()); // Use selection color if selected
                }

                label.setForeground(table.getForeground()); // Default text color

                // Specific styling for Quantity column (low stock)
                if (column == 2) { // Quantity column
                    label.setHorizontalAlignment(JLabel.CENTER); // Center quantity text
                    try {
                        int quantity = Integer.parseInt(value.toString());
                        if (quantity <= LOW_STOCK_THRESHOLD) {
                            label.setBackground(new Color(255, 102, 102)); // Light Red for low stock
                            label.setText("<html><b><font color='white'>" + value.toString() + " ⚠</font></b></html>");
                            label.setForeground(Color.WHITE); // Ensure foreground is white for low stock
                        } else {
                            label.setText("<html>" + value.toString() + "</html>");
                        }
                    } catch (NumberFormatException e) {
                        label.setText("<html>" + value.toString() + "</html>");
                    }
                } else if (column == 3) { // Price column
                    label.setHorizontalAlignment(JLabel.RIGHT); // Right align price
                    label.setText("<html>" + String.format("%.2f", Double.parseDouble(value.toString())) + "</html>"); // Format
                                                                                                                       // price
                } else {
                    label.setHorizontalAlignment(JLabel.LEFT); // Left align other columns
                    label.setText("<html>" + value.toString() + "</html>");
                }

                // Apply cell border
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(230, 230, 230)), // Light grey border
                        BorderFactory.createEmptyBorder(5, 5, 5, 5))); // Padding inside cell

                label.setOpaque(true); // Crucial for background color to show

                return label;
            }
        };

        // Apply the custom renderer to all columns of inventory table
        for (int i = 0; i < inventoryTable.getColumnModel().getColumnCount(); i++) {
            inventoryTable.getColumnModel().getColumn(i).setCellRenderer(inventoryCellRenderer);
        }

        // Add action listener to filter category combobox
        filterCategoryBox
                .addActionListener(_ -> loadItems(searchField.getText(), (String) filterCategoryBox.getSelectedItem()));

        // Create popup menu for inventory table row actions
        JPopupMenu inventoryPopupMenu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("✏️ Edit Item");
        JMenuItem deleteItem = new JMenuItem("🗑️ Delete Item"); // Keep delete in right-click menu
        JMenuItem resetRevenueItem = new JMenuItem("♻️ Reset Revenue"); // This is a general revenue reset, not for
                                                                        // individual items
        inventoryPopupMenu.add(editItem);
        inventoryPopupMenu.add(deleteItem);
        inventoryPopupMenu.add(resetRevenueItem);
        inventoryTable.setComponentPopupMenu(inventoryPopupMenu); // Attach popup menu to the inventory table

        // Add mouse listener to handle right-click for inventory popup menu
        inventoryTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = inventoryTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < inventoryTable.getRowCount())
                        inventoryTable.setRowSelectionInterval(row, row); // Select row on right-click
                    else
                        inventoryTable.clearSelection(); // Clear selection if clicking outside a row
                }
            }
        });

        // Action listener for "Edit Item" in inventory popup menu
        editItem.addActionListener(_ -> {
            int row = inventoryTable.getSelectedRow();
            if (row != -1) {
                // Populate input fields with selected row's data
                idField.setText((String) inventoryTable.getValueAt(row, 0));
                nameField.setText((String) inventoryTable.getValueAt(row, 1));
                // Clean HTML/emoji from quantity and price fields before setting
                quantityField.setText(stripHtmlAndEmoji(inventoryTable.getValueAt(row, 2).toString()));
                priceField.setText(stripHtmlAndEmoji(inventoryTable.getValueAt(row, 3).toString()));
                categoryBox.setSelectedItem((String) inventoryTable.getValueAt(row, 4));
            }
        });

        // Action listener for "Delete Item" in inventory popup menu
        deleteItem.addActionListener(_ -> {
            if (!isAuthorized("admin")) // Check for admin authorization
                return;
            int row = inventoryTable.getSelectedRow();
            if (row != -1) {
                String id = (String) inventoryTable.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(null, "Delete item ID: " + id + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    db.deleteItem(id);
                    loadItems(searchField.getText(), (String) filterCategoryBox.getSelectedItem());
                    updateRevenue();
                }
            }
        });

        // Action listener for "Reset Revenue" in inventory popup menu
        resetRevenueItem.addActionListener(_ -> {
            if (!isAuthorized("admin")) // Check for admin authorization
                return;
            int confirm = JOptionPane.showConfirmDialog(this, "Reset all sales data?", "Confirm",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                db.resetRevenue();
                updateRevenue();
                loadMonthlyRevenueSummary(); // Also refresh monthly sales summary
                JOptionPane.showMessageDialog(this, "Revenue reset.");
            }
        });

        // Bottom panel for action buttons and revenue display for Inventory tab
        JPanel inventoryBottomPanel = new JPanel(new BorderLayout());
        JPanel inventoryButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10)); // Center buttons with
                                                                                             // spacing
        String[] actions = { "➕ Add", "✏️ Update", "🔍 Search", "🔁 Refresh", "💰 Sell" };
        for (String a : actions) {
            JButton btn = new JButton(a);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
            btn.setBackground(accentColor);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(120, 40));
            btn.setRolloverEnabled(true);
            btn.addActionListener(_ -> handleAction(a.replaceAll("[^a-zA-Z]", "")));
            inventoryButtonPanel.add(btn);
        }
        inventoryButtonPanel.setBackground(primaryColor);
        inventoryBottomPanel.add(inventoryButtonPanel, BorderLayout.CENTER);

        revenueLabel = new JLabel("Total Revenue: PHP 0.00", SwingConstants.CENTER);
        revenueLabel.setForeground(textColor);
        revenueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        revenueLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inventoryBottomPanel.setBackground(primaryColor);
        inventoryBottomPanel.add(revenueLabel, BorderLayout.SOUTH);
        inventoryPanel.add(inventoryBottomPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Inventory Management", inventoryPanel);

        // -------------------- Sales & Analytics Tab --------------------
        JPanel salesAnalyticsPanel = new JPanel(new BorderLayout());
        salesAnalyticsPanel.setBackground(lightGreyBackground);

        // Top section for Monthly Revenue Summary
        JPanel monthlySummaryPanel = new JPanel(new BorderLayout());
        monthlySummaryPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(primaryColor),
                "Monthly Revenue Summary",
                0, 0, new Font("Segoe UI", Font.BOLD, 16), primaryColor));
        monthlySummaryPanel.setBackground(lightGreyBackground);

        // Year filter and annual revenue display for monthly summary
        JPanel monthlyFilterAndTotalPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        monthlyFilterAndTotalPanel.setBackground(primaryColor);
        monthlyFilterAndTotalPanel.add(createStyledLabel("Filter by Year:"));

        // Populate year filter with unique years from sales data
        Vector<String> years = new Vector<>();
        years.add("All Years"); // Option to show all years
        try (ResultSet rs = db.getMonthlyRevenueSummary(0)) { // Pass 0 to get all years
            while (rs.next()) {
                String monthYear = rs.getString("month_year");
                String year = monthYear.substring(0, 4);
                if (!years.contains(year)) {
                    years.add(year);
                }
            }
            // Sort years in descending order (most recent first)
            Collections.sort(years.subList(1, years.size()), Collections.reverseOrder());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        yearFilterComboBox = createStyledComboBox(years.toArray(new String[0]));
        yearFilterComboBox.setSelectedItem(String.valueOf(LocalDate.now().getYear())); // Set default to current year
        yearFilterComboBox.addActionListener(_ -> loadMonthlyRevenueSummary()); // Reload on year change
        monthlyFilterAndTotalPanel.add(yearFilterComboBox);

        JButton exportMonthlyRevenueButton = new JButton("Export to CSV");
        exportMonthlyRevenueButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        exportMonthlyRevenueButton.setBackground(accentColor);
        exportMonthlyRevenueButton.setForeground(Color.WHITE);
        exportMonthlyRevenueButton.setBorderPainted(false);
        exportMonthlyRevenueButton.setFocusPainted(false);
        exportMonthlyRevenueButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportMonthlyRevenueButton.setPreferredSize(new Dimension(160, 40));
        exportMonthlyRevenueButton.setRolloverEnabled(true);
        exportMonthlyRevenueButton.addActionListener(_ -> exportMonthlyRevenueToCsv());
        monthlyFilterAndTotalPanel.add(exportMonthlyRevenueButton);

        annualRevenueLabel = new JLabel("Annual Revenue: PHP 0.00", SwingConstants.CENTER);
        annualRevenueLabel.setForeground(textColor);
        annualRevenueLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        annualRevenueLabel.setBorder(new EmptyBorder(5, 10, 5, 10));
        annualRevenueLabel.setBackground(primaryColor);
        annualRevenueLabel.setOpaque(true); // Needed for background color
        monthlyFilterAndTotalPanel.add(annualRevenueLabel);

        monthlySummaryPanel.add(monthlyFilterAndTotalPanel, BorderLayout.NORTH);

        monthlyRevenueTableModel = new DefaultTableModel(new String[] { "Month/Year", "Total Revenue" }, 0);
        monthlyRevenueTable = new JTable(monthlyRevenueTableModel);
        monthlyRevenueTable.setRowHeight(30);
        monthlyRevenueTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        monthlyRevenueTable.setSelectionBackground(new Color(174, 214, 241));
        monthlyRevenueTable.getTableHeader().setBackground(lightAccentColor);
        monthlyRevenueTable.getTableHeader().setForeground(Color.BLACK);
        monthlyRevenueTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 16));
        monthlyRevenueTable.getTableHeader().setDefaultRenderer(inventoryHeaderRenderer); // Reuse header renderer

        // Custom cell renderer for monthly revenue table (for striped rows and price
        // formatting)
        DefaultTableCellRenderer monthlySummaryCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                        column);

                if (!isSelected) {
                    label.setBackground(row % 2 == 0 ? evenRowColor : oddRowColor);
                } else {
                    label.setBackground(table.getSelectionBackground());
                }
                label.setForeground(table.getForeground());

                if (column == 1) { // Total Revenue column
                    label.setHorizontalAlignment(JLabel.RIGHT);
                    label.setText("<html>" + String.format("%.2f", Double.parseDouble(value.toString())) + "</html>");
                } else {
                    label.setHorizontalAlignment(JLabel.LEFT);
                    label.setText("<html>" + value.toString() + "</html>");
                }

                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(230, 230, 230)),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)));
                label.setOpaque(true);
                return label;
            }
        };
        for (int i = 0; i < monthlyRevenueTable.getColumnModel().getColumnCount(); i++) {
            monthlyRevenueTable.getColumnModel().getColumn(i).setCellRenderer(monthlySummaryCellRenderer);
        }

        JScrollPane monthlySummaryScrollPane = new JScrollPane(monthlyRevenueTable);
        monthlySummaryScrollPane.setPreferredSize(new Dimension(800, 200)); // Give it a preferred size
        monthlySummaryScrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        monthlySummaryPanel.add(monthlySummaryScrollPane, BorderLayout.CENTER);
        salesAnalyticsPanel.add(monthlySummaryPanel, BorderLayout.CENTER); // Now fills the center of
                                                                           // salesAnalyticsPanel

        tabbedPane.addTab("Sales & Analytics", salesAnalyticsPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Initial loading of items and revenue for the inventory tab
        loadItems();
        updateRevenue();

        // Initial loading of monthly revenue summary
        loadMonthlyRevenueSummary();

        // Right-click menu for monthlyRevenueTable
        JPopupMenu monthlyRevenuePopupMenu = new JPopupMenu();
        JMenuItem viewIndividualSalesItem = new JMenuItem("View Individual Sales");
        monthlyRevenuePopupMenu.add(viewIndividualSalesItem);
        monthlyRevenueTable.setComponentPopupMenu(monthlyRevenuePopupMenu);

        monthlyRevenueTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = monthlyRevenueTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < monthlyRevenueTable.getRowCount())
                        monthlyRevenueTable.setRowSelectionInterval(row, row);
                    else
                        monthlyRevenueTable.clearSelection();
                }
            }
        });

        viewIndividualSalesItem.addActionListener(_ -> {
            int row = monthlyRevenueTable.getSelectedRow();
            if (row != -1) {
                String yearMonth = (String) monthlyRevenueTable.getValueAt(row, 0);
                showIndividualSalesDialog(yearMonth);
            }
        });
    }

    // Helper method to strip HTML tags and special characters
    private String stripHtmlAndEmoji(String htmlString) {
        // Regex to remove HTML tags and the specific warning emoji
        Pattern pattern = Pattern.compile("<html>|</html>|<b>|<\\/b>|<font color='white'>|<\\/font>|⚠|\\s+");
        Matcher matcher = pattern.matcher(htmlString);
        return matcher.replaceAll("").trim();
    }

    // Helper method for GridBagConstraints
    private GridBagConstraints gbc(int gridx, int gridy, GridBagConstraints constraints) {
        constraints.gridx = gridx;
        constraints.gridy = gridy;
        return constraints;
    }

    // Helper method to create styled JTextFields
    private JTextField createStyledTextField() {
        JTextField field = new JTextField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10))); // Increased padding
        return field;
    }

    // Helper method to create styled JComboBoxes
    private JComboBox<String> createStyledComboBox(String[] items) {
        JComboBox<String> comboBox = new JComboBox<>(items);
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        comboBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10))); // Increased padding
        return comboBox;
    }

    // Helper method to create styled JLabels
    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(220, 220, 220)); // Slightly lighter white for labels
        label.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Consistent bold font
        return label;
    }

    // Checks if the current user has the required role
    private boolean isAuthorized(String requiredRole) {
        if (requiredRole.equals("admin") && !currentRole.equals("admin")) {
            JOptionPane.showMessageDialog(this, "Admin access required.");
            return false;
        }
        if (requiredRole.equals("staff") && currentRole.equals("viewer")) {
            JOptionPane.showMessageDialog(this, "Only staff/admin can perform this.");
            return false;
        }
        return true;
    }

    // Handles actions triggered by buttons
    private void handleAction(String action) {
        String id = idField.getText();
        String name = nameField.getText();
        String qtyStr = quantityField.getText();
        String priceStr = priceField.getText();
        String category = (String) categoryBox.getSelectedItem();
        String search = searchField.getText(); // Current search keyword

        switch (action) {
            case "Add" -> {
                if (!isAuthorized("staff"))
                    return;
                if (validateInput()) {
                    db.addItem(new InventoryItem(id, name, Integer.parseInt(qtyStr), Double.parseDouble(priceStr),
                            category));
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                    clearInputFields(); // Clear fields after adding
                    loadMonthlyRevenueSummary(); // Refresh monthly summary
                }
            }
            case "Update" -> {
                if (!isAuthorized("staff"))
                    return;
                if (validateInput()) {
                    db.updateItem(new InventoryItem(id, name, Integer.parseInt(qtyStr), Double.parseDouble(priceStr),
                            category));
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                    clearInputFields(); // Clear fields after updating
                    loadMonthlyRevenueSummary(); // Refresh monthly summary
                }
            }
            case "Search" -> loadItems(search, (String) filterCategoryBox.getSelectedItem());
            case "Refresh" -> {
                loadItems();
                updateRevenue();
                clearInputFields();
                searchField.setText("");
                filterCategoryBox.setSelectedItem("All");
                loadMonthlyRevenueSummary(); // Refresh monthly summary
            }
            case "Sell" -> {
                if (!isAuthorized("staff"))
                    return;
                try {
                    int qtyToSell = Integer.parseInt(sellQtyField.getText());
                    int selectedRow = inventoryTable.getSelectedRow(); // Use inventoryTable
                    if (selectedRow == -1) {
                        JOptionPane.showMessageDialog(this, "Please select an item to sell.");
                        return;
                    }
                    String itemIdToSell = (String) inventoryTable.getValueAt(selectedRow, 0);
                    // When getting value from table, clean the HTML tags and emoji first
                    int currentQuantity = Integer
                            .parseInt(stripHtmlAndEmoji(inventoryTable.getValueAt(selectedRow, 2).toString()));
                    double priceAtSale = Double
                            .parseDouble(stripHtmlAndEmoji(inventoryTable.getValueAt(selectedRow, 3).toString()));

                    if (qtyToSell <= 0) {
                        JOptionPane.showMessageDialog(this, "Quantity to sell must be positive.");
                        return;
                    }

                    if (qtyToSell > currentQuantity) {
                        JOptionPane.showMessageDialog(this, "Not enough stock. Available: " + currentQuantity);
                        return;
                    }

                    db.recordSale(itemIdToSell, qtyToSell, priceAtSale, LocalDate.now().toString());
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                    updateRevenue();
                    loadMonthlyRevenueSummary(); // Refresh monthly summary
                    sellQtyField.setText(""); // Clear sell quantity field
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid quantity for selling.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error during sale: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // Clears the input fields
    private void clearInputFields() {
        idField.setText("");
        nameField.setText("");
        quantityField.setText("");
        priceField.setText("");
        categoryBox.setSelectedItem("Electronics"); // Reset to default
    }

    // Validates the input fields for adding/updating items
    private boolean validateInput() {
        try {
            if (idField.getText().isEmpty() || nameField.getText().isEmpty() || quantityField.getText().isEmpty()
                    || priceField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields must be filled.");
                return false;
            }
            Integer.parseInt(quantityField.getText());
            Double.parseDouble(priceField.getText());
            return true;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number format for quantity or price.");
            return false;
        }
    }

    // Loads all items into the inventory table
    private void loadItems() {
        loadItems(null, "All");
    }

    // Loads items based on search keyword and category filter into inventory table
    private void loadItems(String search, String categoryFilter) {
        tableModel.setRowCount(0); // Clear existing table data
        ResultSet rs = (search == null || search.isEmpty()) ? db.getAllItems() : db.searchItems(search);
        try {
            while (rs.next()) {
                String category = rs.getString("category");
                if (categoryFilter.equals("All") || category.equals(categoryFilter)) {
                    Vector<String> row = new Vector<>();
                    row.add(rs.getString("id"));
                    row.add(rs.getString("name"));
                    row.add(String.valueOf(rs.getInt("quantity")));
                    row.add(String.valueOf(rs.getDouble("price")));
                    row.add(category);
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Updates the displayed total revenue (for Inventory tab)
    private void updateRevenue() {
        revenueLabel.setText("Total Revenue: PHP " + String.format("%.2f", db.getTotalRevenue()));
    }

    // Loads monthly revenue summary into monthlyRevenueTable based on selected year
    private void loadMonthlyRevenueSummary() {
        monthlyRevenueTableModel.setRowCount(0); // Clear existing data

        String selectedYearStr = (String) yearFilterComboBox.getSelectedItem();
        int yearToFilter = 0; // Default to all years
        if (selectedYearStr != null && !selectedYearStr.equals("All Years")) {
            yearToFilter = Integer.parseInt(selectedYearStr);
        }

        ResultSet rs = db.getMonthlyRevenueSummary(yearToFilter);
        double annualTotal = 0.0;
        try {
            while (rs.next()) {
                Vector<String> row = new Vector<>();
                row.add(rs.getString("month_year"));
                double monthlyRevenue = rs.getDouble("total_revenue");
                row.add(String.valueOf(monthlyRevenue));
                monthlyRevenueTableModel.addRow(row);
                annualTotal += monthlyRevenue; // Accumulate for annual total
            }
            annualRevenueLabel.setText("Annual Revenue: PHP " + String.format("%.2f", annualTotal));
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading monthly revenue summary.", "Database Error",
                    JOptionPane.ERROR_MESSAGE);
            annualRevenueLabel.setText("Annual Revenue: PHP 0.00"); // Reset if error
        }
    }

    // Exports the monthly revenue summary to a CSV file
    private void exportMonthlyRevenueToCsv() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Monthly Revenue Report");
        fileChooser.setSelectedFile(new File("Monthly_Revenue_Report.csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
            }

            try (FileWriter csvWriter = new FileWriter(fileToSave)) {
                // Write header
                for (int i = 0; i < monthlyRevenueTableModel.getColumnCount(); i++) {
                    csvWriter.append(monthlyRevenueTableModel.getColumnName(i));
                    if (i < monthlyRevenueTableModel.getColumnCount() - 1) {
                        csvWriter.append(",");
                    }
                }
                csvWriter.append("\n");

                // Write data rows
                for (int i = 0; i < monthlyRevenueTableModel.getRowCount(); i++) {
                    for (int j = 0; j < monthlyRevenueTableModel.getColumnCount(); j++) {
                        String value = monthlyRevenueTableModel.getValueAt(i, j).toString();
                        // Handle values that might contain commas by quoting them
                        if (value.contains(",")) {
                            csvWriter.append("\"").append(value).append("\"");
                        } else {
                            csvWriter.append(value);
                        }
                        if (j < monthlyRevenueTableModel.getColumnCount() - 1) {
                            csvWriter.append(",");
                        }
                    }
                    csvWriter.append("\n");
                }

                // Add annual revenue to the CSV
                csvWriter.append("\n"); // Blank line for separation
                csvWriter.append("Total Annual Revenue,");
                csvWriter.append(String.format("%.2f", Double.parseDouble(
                        stripHtmlAndEmoji(annualRevenueLabel.getText()).replace("Annual Revenue: PHP ", ""))));
                csvWriter.append("\n");

                JOptionPane.showMessageDialog(this,
                        "Monthly revenue report exported successfully to:\n" + fileToSave.getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error exporting report: " + ex.getMessage(), "Export Error",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Error parsing annual revenue for export: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * Shows a dialog with individual sales for a given month and year.
     * 
     * @param yearMonth The month and year in YYYY-MM format.
     */
    private void showIndividualSalesDialog(String yearMonth) {
        JDialog salesDialog = new JDialog(this, "Individual Sales for " + yearMonth, true); // Modal dialog
        salesDialog.setSize(700, 400);
        salesDialog.setLocationRelativeTo(this); // Center relative to main frame
        salesDialog.setLayout(new BorderLayout());

        // Create table for individual sales
        DefaultTableModel dialogTableModel = new DefaultTableModel(
                new String[] { "Sale ID", "Item ID", "Item Name", "Quantity Sold", "Price Sold", "Sale Date" }, 0);
        JTable dialogSalesTable = new JTable(dialogTableModel);
        dialogSalesTable.setRowHeight(25);
        dialogSalesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dialogSalesTable.setSelectionBackground(new Color(174, 214, 241));

        // Reuse header renderer for dialog table
        DefaultTableCellRenderer dialogHeaderRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(JLabel.CENTER);
                c.setBackground(new Color(41, 128, 185)); // Light blue header
                c.setForeground(Color.BLACK);
                c.setFont(new Font("Segoe UI", Font.BOLD, 14));
                ((JComponent) c).setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(150, 150, 150)));
                return c;
            }
        };
        dialogSalesTable.getTableHeader().setDefaultRenderer(dialogHeaderRenderer);

        // Custom cell renderer for dialog table (striped rows, alignment)
        DefaultTableCellRenderer dialogCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                        column);
                Color evenRowColor = new Color(248, 248, 248); // Very light grey
                Color oddRowColor = Color.WHITE; // White

                if (!isSelected) {
                    label.setBackground(row % 2 == 0 ? evenRowColor : oddRowColor);
                } else {
                    label.setBackground(table.getSelectionBackground());
                }
                label.setForeground(table.getForeground());

                if (column == 3 || column == 4) { // Quantity Sold, Price Sold
                    label.setHorizontalAlignment(JLabel.RIGHT);
                    if (column == 4) { // Format Price Sold
                        label.setText(String.format("%.2f", Double.parseDouble(value.toString())));
                    }
                } else {
                    label.setHorizontalAlignment(JLabel.LEFT);
                }

                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, new Color(230, 230, 230)));
                label.setOpaque(true);
                return label;
            }
        };
        for (int i = 0; i < dialogSalesTable.getColumnModel().getColumnCount(); i++) {
            dialogSalesTable.getColumnModel().getColumn(i).setCellRenderer(dialogCellRenderer);
        }

        JScrollPane dialogScrollPane = new JScrollPane(dialogSalesTable);
        dialogScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        salesDialog.add(dialogScrollPane, BorderLayout.CENTER);

        // Populate table with data
        double monthlyTotal = 0.0;
        try (ResultSet rs = db.getIndividualSalesForMonth(yearMonth)) {
            while (rs.next()) {
                Vector<Object> rowData = new Vector<>();
                rowData.add(rs.getInt("sale_id"));
                rowData.add(rs.getString("item_id"));
                rowData.add(rs.getString("item_name"));
                rowData.add(rs.getInt("quantity_sold"));
                rowData.add(rs.getDouble("price_sold"));
                rowData.add(rs.getString("date"));
                dialogTableModel.addRow(rowData);
                monthlyTotal += (rs.getInt("quantity_sold") * rs.getDouble("price_sold"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(salesDialog, "Error loading individual sales: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }

        // Display total revenue for the month in the dialog
        JLabel dialogTotalLabel = new JLabel(
                "Total Revenue for " + yearMonth + ": PHP " + String.format("%.2f", monthlyTotal),
                SwingConstants.CENTER);
        dialogTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        dialogTotalLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
        dialogTotalLabel.setBackground(new Color(220, 230, 240));
        dialogTotalLabel.setOpaque(true);
        salesDialog.add(dialogTotalLabel, BorderLayout.SOUTH);

        // Add right-click delete functionality to this dialog's table
        JPopupMenu dialogTablePopupMenu = new JPopupMenu();
        JMenuItem deleteSaleItem = new JMenuItem("🗑️ Delete Sale");
        dialogTablePopupMenu.add(deleteSaleItem);
        dialogSalesTable.setComponentPopupMenu(dialogTablePopupMenu);

        dialogSalesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = dialogSalesTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < dialogSalesTable.getRowCount())
                        dialogSalesTable.setRowSelectionInterval(row, row);
                    else
                        dialogSalesTable.clearSelection();
                }
            }
        });

        deleteSaleItem.addActionListener(_ -> {
            if (!isAuthorized("admin")) { // Ensure only admin can delete sales
                return;
            }
            int row = dialogSalesTable.getSelectedRow();
            if (row != -1) {
                // Get the Sale ID from the first column of the selected row
                int saleId = (Integer) dialogTableModel.getValueAt(row, 0);

                int confirm = JOptionPane.showConfirmDialog(salesDialog,
                        "Delete Sale ID: " + saleId + "? This cannot be undone.", "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    db.deleteSale(saleId);
                    JOptionPane.showMessageDialog(salesDialog, "Sale ID " + saleId + " deleted successfully.",
                            "Deletion Complete", JOptionPane.INFORMATION_MESSAGE);
                    salesDialog.dispose(); // Close the dialog
                    loadMonthlyRevenueSummary(); // Refresh the main monthly summary table
                }
            }
        });

        salesDialog.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            // Set System Look and Feel for a native appearance
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // Apply custom fonts to UIManager defaults
            UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI for consistency
            UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI
            UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI
            UIManager.put("PasswordField.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI
            UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI
            UIManager.put("Table.font", new Font("Segoe UI", Font.PLAIN, 14)); // Changed to Segoe UI
            UIManager.put("TableHeader.font", new Font("Segoe UI", Font.BOLD, 14)); // Changed to Segoe UI
            UIManager.put("TabbedPane.font", new Font("Segoe UI", Font.BOLD, 14)); // Set font for tabs
        } catch (Exception ignored) {
            // Ignore exceptions if look and feel cannot be set
        }

        SwingUtilities.invokeLater(() -> {
            DatabaseManager db = new DatabaseManager();
            LoginFrame loginFrame = new LoginFrame(db);
            loginFrame.setVisible(true);
        });
    }
}
