package com.example.goidoan.model;

public enum TableStatus {
    AVAILABLE("Bàn trống", "Bàn sạch sẵn sàng đón khách"),
    OCCUPIED("Đang ăn", "Khách đang dùng bữa"),
    WAITING_PAYMENT("Chờ tính tiền", "Khách đã yêu cầu thanh toán");

    private final String displayName;
    private final String description;

    TableStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
