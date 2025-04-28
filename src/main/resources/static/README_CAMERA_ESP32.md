# Hướng dẫn cài đặt Ping-Pong cho ESP32 Camera

Để đảm bảo server có thể phát hiện khi ESP32 bị mất điện hoặc mất kết nối, chúng ta cần thêm cơ chế ping-pong vào mã nguồn ESP32.

## Cập nhật code ESP32

Thêm đoạn code sau vào phần xử lý WebSocket của ESP32:

```cpp
// Xử lý tin nhắn từ server
void handleWebSocketMessage(void *arg, uint8_t *data, size_t len) {
  // Code hiện tại của bạn...
  
  // Thêm xử lý ping từ server
  if (len == 4 && strncmp((char *)data, "ping", 4) == 0) {
    // Nhận được ping, trả về pong
    webSocket.sendTXT("pong");
    Serial.println("Ping received, sent pong response");
    return;
  }
  
  // Tiếp tục xử lý các tin nhắn khác...
}
```

## Lợi ích của cơ chế ping-pong

1. Server có thể phát hiện khi ESP32 mất kết nối ngay cả khi không có thông báo ngắt kết nối rõ ràng
2. Cập nhật trạng thái camera trong cơ sở dữ liệu thành offline khi cần
3. Tự động dọn dẹp tài nguyên không sử dụng
4. Giảm tải cho server khi không còn phải gửi dữ liệu đến camera đã mất kết nối

## Cách hoạt động

1. Server sẽ gửi tin nhắn "ping" đến mỗi camera đã kết nối sau mỗi 20 giây
2. ESP32 cần phản hồi bằng tin nhắn "pong"
3. Nếu server không nhận được bất kỳ hoạt động nào (bao gồm cả "pong") từ ESP32 trong 60 giây, nó sẽ đánh dấu camera đó là không hoạt động và cập nhật trạng thái thành offline

## Các bước triển khai

1. Cập nhật code ESP32 như mô tả ở trên
2. Tải lại code lên ESP32
3. Kiểm tra trong log của server để xác nhận ping-pong hoạt động đúng

## Kiểm tra hoạt động

Bạn có thể kiểm tra cơ chế này bằng cách:
1. Kết nối ESP32 với server
2. Quan sát log của ESP32 để thấy nhận "ping" và gửi "pong"
3. Ngắt kết nối mạng hoặc rút nguồn ESP32
4. Kiểm tra log server - sau khoảng 1 phút, server sẽ đánh dấu camera là offline 