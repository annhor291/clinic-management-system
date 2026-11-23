package com.example.clinic.controller;

import com.example.clinic.dto.request.CreatePaymentRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PaymentResponse;
import com.example.clinic.entity.enums.PaymentStatus;
import com.example.clinic.service.PaymentRefundService;
import com.example.clinic.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRefundService paymentRefundService;

    // POST /api/v1/payments
    // Tạo giao dịch thanh toán, trả về paymentUrl để FE chuyển hướng khách sang cổng thanh toán
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        PaymentResponse response = paymentService.createPayment(request, resolveClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Tạo giao dịch thanh toán thành công", response));
    }

    // Đi qua ngrok/proxy thì IP thật nằm ở X-Forwarded-For; IPv6 localhost đổi về 127.0.0.1
    // vì VNPay yêu cầu vnp_IpAddr dạng IP hợp lệ
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) ? "127.0.0.1" : ip;
    }

    // GET /api/v1/payments/vnpay-ipn — VNPay server gọi, không có JWT.
// Phải trả HTTP 200 với JSON RspCode/Message; mã lạ hoặc timeout thì VNPay gọi lại (tối đa 10 lần, cách 5 phút).
    @GetMapping("/vnpay-ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        log.info("VNPay IPN: txnRef={}, responseCode={}", params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));
        try {
            return paymentService.handleVnPayIpn(params);
        } catch (Exception e) {
            log.error("Lỗi xử lý VNPay IPN txnRef={}", params.get("vnp_TxnRef"), e);
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
    }

    // GET /api/v1/payments/{transactionCode} — FE gọi sau khi VNPay redirect về, để lấy trạng thái thật
    @GetMapping("/{transactionCode}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByTransactionCode(@PathVariable String transactionCode) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin giao dịch thành công",
                paymentService.getByTransactionCode(transactionCode)));
    }

    // POST /api/v1/payments/{id}/refund — ADMIN thử lại hoàn tiền cho giao dịch REFUND_FAILED
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryRefund(@PathVariable Long id) {
        PaymentStatus status = paymentRefundService.retryFailedRefund(id);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lại yêu cầu hoàn tiền",
                Map.of("status", status.name())));
    }
}
