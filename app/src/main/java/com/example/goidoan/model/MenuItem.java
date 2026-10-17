package com.example.goidoan.model;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public class MenuItem implements Serializable {
    private String id;
    private String name;
    private String category;
    private long price;
    private String description;
    private String badge;
    private boolean isAvailable;

    public MenuItem(String id, String name, String category, long price, String description, String badge, boolean isAvailable) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.badge = badge;
        this.isAvailable = isAvailable;
    }

    public MenuItem(String id, String name, long price) {
        this(id, name, "Món", price, "", "", true);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public long getPrice() {
        return price;
    }

    public String getFormattedPrice() {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(price) + " đ";
    }

    public String getDescription() {
        return description;
    }

    public String getBadge() {
        return badge;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }
}
