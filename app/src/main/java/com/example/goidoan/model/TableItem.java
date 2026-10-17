package com.example.goidoan.model;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TableItem implements Serializable {
    private String id;
    private String name;
    private int capacity;
    private TableStatus status;
    private String occupiedTime;
    private List<OrderItem> orderedItems;

    public TableItem(String id, String name, int capacity, TableStatus status, String occupiedTime) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
        this.occupiedTime = occupiedTime;
        this.orderedItems = new ArrayList<>();
    }

    public TableItem(String id, String name) {
        this(id, name, 4, TableStatus.OCCUPIED, "");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public String getOccupiedTime() {
        return occupiedTime;
    }

    public void setOccupiedTime(String occupiedTime) {
        this.occupiedTime = occupiedTime;
    }

    public List<OrderItem> getOrderedItems() {
        return orderedItems;
    }

    public void setOrderedItems(List<OrderItem> items) {
        this.orderedItems = items != null ? items : new ArrayList<>();
    }

    public void addOrderedItems(List<OrderItem> items) {
        if (items == null) return;
        for (OrderItem newItem : items) {
            boolean found = false;
            for (OrderItem existing : this.orderedItems) {
                if (existing.getMenuItem().getId().equals(newItem.getMenuItem().getId())) {
                    existing.setQuantity(existing.getQuantity() + newItem.getQuantity());
                    if (newItem.getNote() != null && !newItem.getNote().isEmpty()) {
                        existing.setNote(existing.getNote() + ", " + newItem.getNote());
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                this.orderedItems.add(newItem);
            }
        }
    }

    public long getTotalBill() {
        long total = 0;
        if (orderedItems != null) {
            for (OrderItem item : orderedItems) {
                total += item.getTotalPrice();
            }
        }
        return total;
    }

    public String getFormattedTotalBill() {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(getTotalBill()) + " đ";
    }

    public void clearTable() {
        this.status = TableStatus.AVAILABLE;
        this.occupiedTime = "";
        this.orderedItems.clear();
    }
}
