package com.example.clinic.payment.gateway;

import com.example.clinic.entity.Payment;

import java.util.Map;

// Mỗi cổng thanh toán là 1 implementation. Bước 3 và 5 sẽ thêm verifyCallback và refund.
// Thêm Momo sau này chỉ cần viết thêm 1 class implement interface này.
public interface PaymentGatewayService {

    // Mã cổng, khớp payment_methods.code (VD "VNPAY")
    String code();

    // Thời gian sống của 1 giao dịch (phút)
    int paymentExpireMinutes();

    // Dựng URL để chuyển hướng khách sang trang thanh toán của cổng
    String buildPaymentUrl(Payment payment, String orderInfo, String clientIp);

    // Xác thực chữ ký và đọc kết quả callback (IPN) từ cổng
    GatewayCallback parseCallback(Map<String, String> params);

    // Gửi yêu cầu hoàn tiền (toàn phần nếu amount bằng số tiền gốc, ngược lại một phần).
    // requestId: mã yêu cầu cố định cho mỗi lần hoàn của 1 giao dịch.
    RefundResult refund(Payment payment, java.math.BigDecimal amount, String requestId, String createdBy);
}
