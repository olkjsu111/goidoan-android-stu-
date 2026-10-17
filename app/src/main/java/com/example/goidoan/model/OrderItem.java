package com.example.goidoan.model;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderItem implements Serializable {
    private MenuItem menuItem;
    private int quantity;
    private String note;

    public OrderItem(MenuItem menuItem, int quantity, String note) {
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.note = note != null ? note : "";
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getTotalPrice() {
        return menuItem != null ? menuItem.getPrice() * quantity : 0;
    }

    public String getFormattedTotalPrice() {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(getTotalPrice()) + " đ";
    }
}
