
// RevUpApp.java
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

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

class DatabaseManager {
    private Connection conn;

    public DatabaseManager() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:RevUp.db");
            Statement stmt = conn.createStatement();
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, quantity INTEGER, price REAL, category TEXT)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS sales (sale_id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT, quantity_sold INTEGER, price_sold REAL, date TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT, role TEXT)");
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('admin', 'admin123', 'admin')");
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('staff', 'staff123', 'staff')");
            stmt.execute("INSERT OR IGNORE INTO users VALUES ('viewer', 'viewer123', 'viewer')");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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
            JOptionPane.showMessageDialog(null, "ID already exists.");
        }
    }

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

    public void deleteItem(String id) {
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM items WHERE id=?");
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ResultSet getAllItems() {
        try {
            Statement stmt = conn.createStatement();
            return stmt.executeQuery("SELECT * FROM items");
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

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

    public void recordSale(String itemId, int quantitySold, double priceSold, String date) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO sales (item_id, quantity_sold, price_sold, date) VALUES (?, ?, ?, ?)");
            stmt.setString(1, itemId);
            stmt.setInt(2, quantitySold);
            stmt.setDouble(3, priceSold);
            stmt.setString(4, date);
            stmt.executeUpdate();

            PreparedStatement update = conn.prepareStatement("UPDATE items SET quantity = quantity - ? WHERE id = ?");
            update.setInt(1, quantitySold);
            update.setString(2, itemId);
            update.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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

    public void resetRevenue() {
        try {
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM sales");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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
}

public class RevUpApp extends JFrame {
    private JTextField idField, nameField, quantityField, priceField, searchField, sellQtyField;
    private JComboBox<String> categoryBox, filterCategoryBox;
    private DefaultTableModel tableModel;
    private JTable table;
    private JLabel revenueLabel;
    private DatabaseManager db;
    private String currentUser;
    private String currentRole;

    public RevUpApp(String username, String role, DatabaseManager dbManager) {
        this.db = dbManager;
        this.currentUser = username;
        this.currentRole = role;

        setTitle("RevUp Inventory System - Logged in as: " + currentUser + " [" + currentRole + "]");
        setSize(1100, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(2, 8, 5, 5));
        idField = new JTextField();
        nameField = new JTextField();
        quantityField = new JTextField();
        priceField = new JTextField();
        categoryBox = new JComboBox<>(new String[] { "Electronics", "Clothing", "Furniture", "Other" });
        filterCategoryBox = new JComboBox<>(new String[] { "All", "Electronics", "Clothing", "Furniture", "Other" });
        searchField = new JTextField();
        sellQtyField = new JTextField();

        inputPanel.add(new JLabel("ID"));
        inputPanel.add(new JLabel("Name"));
        inputPanel.add(new JLabel("Quantity"));
        inputPanel.add(new JLabel("Price"));
        inputPanel.add(new JLabel("Category"));
        inputPanel.add(new JLabel("Search"));
        inputPanel.add(new JLabel("Sell Qty"));
        inputPanel.add(new JLabel("Filter Category"));

        inputPanel.add(idField);
        inputPanel.add(nameField);
        inputPanel.add(quantityField);
        inputPanel.add(priceField);
        inputPanel.add(categoryBox);
        inputPanel.add(searchField);
        inputPanel.add(sellQtyField);
        inputPanel.add(filterCategoryBox);

        add(inputPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[] { "ID", "Name", "Quantity", "Price", "Category" }, 0);
        table = new JTable(tableModel);
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        sorter.setSortKeys(List.of(
                new RowSorter.SortKey(2, SortOrder.ASCENDING),
                new RowSorter.SortKey(3, SortOrder.ASCENDING)));
        add(new JScrollPane(table), BorderLayout.CENTER);

        filterCategoryBox
                .addActionListener(_ -> loadItems(searchField.getText(), (String) filterCategoryBox.getSelectedItem()));

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit Item");
        JMenuItem deleteItem = new JMenuItem("Delete Item");
        JMenuItem resetRevenueItem = new JMenuItem("Reset Revenue");
        popupMenu.add(editItem);
        popupMenu.add(deleteItem);
        popupMenu.add(resetRevenueItem);
        table.setComponentPopupMenu(popupMenu);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < table.getRowCount()) {
                        table.setRowSelectionInterval(row, row);
                    } else {
                        table.clearSelection();
                    }
                }
            }
        });

        editItem.addActionListener(_ -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                idField.setText((String) table.getValueAt(row, 0));
                nameField.setText((String) table.getValueAt(row, 1));
                quantityField.setText((String) table.getValueAt(row, 2));
                priceField.setText((String) table.getValueAt(row, 3));
                categoryBox.setSelectedItem((String) table.getValueAt(row, 4));
            }
        });

        deleteItem.addActionListener(_ -> {
            if (!isAuthorized("admin"))
                return;
            int row = table.getSelectedRow();
            if (row != -1) {
                String id = (String) table.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(null, "Delete item ID: " + id + "?", "Confirm",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    db.deleteItem(id);
                    loadItems(searchField.getText(), (String) filterCategoryBox.getSelectedItem());
                    updateRevenue();
                }
            }
        });

        resetRevenueItem.addActionListener(_ -> {
            if (!isAuthorized("admin"))
                return;
            int confirm = JOptionPane.showConfirmDialog(this, "Reset all sales data?", "Confirm",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                db.resetRevenue();
                updateRevenue();
                JOptionPane.showMessageDialog(this, "Revenue reset.");
            }
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        String[] actions = { "Add", "Update", "Delete", "Search", "Refresh", "Sell" };
        for (String a : actions) {
            JButton btn = new JButton(a);
            btn.addActionListener(e -> handleAction(e.getActionCommand()));
            buttonPanel.add(btn);
        }

        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        revenueLabel = new JLabel("Total Revenue: PHP 0.00");
        bottomPanel.add(revenueLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        loadItems();
        updateRevenue();
    }

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

    private void handleAction(String action) {
        String id = idField.getText();
        String name = nameField.getText();
        String qtyStr = quantityField.getText();
        String priceStr = priceField.getText();
        String category = (String) categoryBox.getSelectedItem();
        String search = searchField.getText();

        switch (action) {
            case "Add" -> {
                if (!isAuthorized("staff"))
                    return;
                if (validateInput()) {
                    db.addItem(new InventoryItem(id, name, Integer.parseInt(qtyStr), Double.parseDouble(priceStr),
                            category));
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                }
            }
            case "Update" -> {
                if (!isAuthorized("staff"))
                    return;
                if (validateInput()) {
                    db.updateItem(new InventoryItem(id, name, Integer.parseInt(qtyStr), Double.parseDouble(priceStr),
                            category));
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                }
            }
            case "Delete" -> {
                if (!isAuthorized("admin"))
                    return;
                db.deleteItem(id);
                loadItems(search, (String) filterCategoryBox.getSelectedItem());
            }
            case "Search" -> loadItems(search, (String) filterCategoryBox.getSelectedItem());
            case "Refresh" -> {
                loadItems();
                updateRevenue();
            }
            case "Sell" -> {
                if (!isAuthorized("staff"))
                    return;
                try {
                    int qtyToSell = Integer.parseInt(sellQtyField.getText());
                    double price = Double.parseDouble(priceStr);
                    db.recordSale(id, qtyToSell, price, LocalDate.now().toString());
                    loadItems(search, (String) filterCategoryBox.getSelectedItem());
                    updateRevenue();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Invalid sell inputs.");
                }
            }
        }
    }

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
            JOptionPane.showMessageDialog(this, "Invalid number format.");
            return false;
        }
    }

    private void loadItems() {
        loadItems(null, "All");
    }

    private void loadItems(String search, String categoryFilter) {
        tableModel.setRowCount(0);
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

    private void updateRevenue() {
        revenueLabel.setText("Total Revenue: PHP " + String.format("%.2f", db.getTotalRevenue()));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            DatabaseManager db = new DatabaseManager();
            JTextField userField = new JTextField();
            JPasswordField passField = new JPasswordField();
            Object[] msg = { "Username:", userField, "Password:", passField };

            while (true) {
                int opt = JOptionPane.showConfirmDialog(null, msg, "Login", JOptionPane.OK_CANCEL_OPTION);
                if (opt != JOptionPane.OK_OPTION)
                    System.exit(0);

                String role = db.authenticate(userField.getText(), new String(passField.getPassword()));
                if (role != null) {
                    new RevUpApp(userField.getText(), role, db).setVisible(true);
                    break;
                } else {
                    JOptionPane.showMessageDialog(null, "Invalid credentials.");
                }
            }
        });
    }
}