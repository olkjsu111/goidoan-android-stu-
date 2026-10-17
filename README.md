# Quán Cơm POS - Ứng Dụng Android Quản Lý Gọi Món & Thanh Toán

Ứng dụng Android Native kết hợp WebView tốc độ cao dành cho hệ thống quản lý gọi món tại bàn, chế biến KDS bếp và thanh toán VietQR tự động.

## 📱 Tính Năng Nổi Bật

- **Thanh toán VietQR Napas 247 Native & Tự động**:
  - Hỗ trợ hiển thị mã QR BIDV của nhà hàng trực tiếp từ bộ nhớ drawable (`my_qr.jpg`).
  - Hỗ trợ sinh mã VietQR động theo số tiền hóa đơn và số bàn.
  - Tự động đồng bộ với hệ thống thu ngân.
- **Hệ thống Android Bridge thông minh**:
  - Tương tác 2 chiều giữa WebView và Native Android.
  - Tự động chặn và chuẩn hóa dữ liệu thanh toán ngân hàng.
  - Hỗ trợ xuất báo cáo doanh thu Excel và thông báo hệ thống.
- **Hỗ trợ đa môi trường máy chủ**:
  - Máy chủ Cloud: `https://quancom-pos.vercel.app`
  - Máy ảo Android Studio Emulator: `http://10.0.2.2:3000`
  - Mạng nội bộ LAN / Wi-Fi: `http://192.168.1.x:3000`
  - Hỗ trợ nhập URL tùy chỉnh trực tiếp trên ứng dụng.
- **Tối ưu trải nghiệm (UX)**:
  - Giao diện Dark Mode sang trọng, độ tương phản cao.
  - Splash Screen khởi động mượt mà.
  - Kéo để làm mới trang (Swipe Refresh).
  - Tự động xử lý trạng thái mất kết nối mạng / lỗi máy chủ.

## 🛠️ Công Nghệ Sử Dụng

- **Ngôn ngữ**: Java 11 / 17
- **Target SDK**: Android 35 / compileSdk 37
- **Min SDK**: Android 7.0 (API 24)
- **Kiến trúc**: WebView + Native Android Bridge + Material 3 Components
- **Thư viện chính**:
  - `androidx.webkit:webkit`
  - `androidx.swiperefreshlayout:swiperefreshlayout`
  - `androidx.core:core-splashscreen`
  - `com.google.android.material:material`

## 🚀 Hướng Dẫn Cài Đặt & Chạy

1. Mở thư mục này bằng **Android Studio**.
2. Đợi Gradle đồng bộ (Sync Project with Gradle Files).
3. Chọn máy ảo Android (Pixel 7 hoặc thiết bị thật kết nối qua cáp / Wi-Fi).
4. Nhấn **Run** (`Shift + F10`) để cài đặt và khởi chạy.

---
© 2026 Quán Cơm POS. All rights reserved.
