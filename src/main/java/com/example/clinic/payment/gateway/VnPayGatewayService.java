package com.example.clinic.payment.gateway;

import com.example.clinic.entity.Payment;
import com.example.clinic.payment.config.VnPayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VnPayGatewayService implements PaymentGatewayService {

    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties props;
    private final VnPayApiClient apiClient;

    @Override
    public String code() {
        return "VNPAY";
    }

    @Override
    public int paymentExpireMinutes() {
        return props.getPaymentExpireMinutes();
    }

    @Override
    public String buildPaymentUrl(Payment payment, String orderInfo, String clientIp) {
        if (isBlank(props.getTmnCode()) || isBlank(props.getHashSecret())) {
            throw new IllegalStateException("VNPay chưa được cấu hình (thiếu VNPAY_TMN_CODE hoặc VNPAY_HASH_SECRET)");
        }

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.getTmnCode());
        params.put("vnp_Amount", toVnpAmount(payment.getAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getTransactionCode());
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", props.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        // Giờ GMT+7: server chạy giờ Việt Nam (khớp serverTimezone trong JDBC URL)
        String createDate = format(payment.getCreatedAt());
        payment.setGatewayCreateDate(createDate);   // lưu lại để hoàn tiền gửi đúng giá trị này
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", format(payment.getExpiredAt()));

        String signedData = VnPayUtil.buildSignedData(params);
        String secureHash = VnPayUtil.hmacSha512(props.getHashSecret(), signedData);

        return props.getPayUrl() + "?" + signedData + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    public GatewayCallback parseCallback(Map<String, String> rawParams) {
        Map<String, String> data = new HashMap<>();
        rawParams.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                data.put(k, v);
            }
        });

        boolean valid = false;
        String received = rawParams.get("vnp_SecureHash");
        if (!isBlank(props.getHashSecret()) && !isBlank(received)) {
            String expected = VnPayUtil.hmacSha512(props.getHashSecret(), VnPayUtil.buildSignedData(data));
            valid = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.toLowerCase().getBytes(StandardCharsets.UTF_8));
        }

        BigDecimal amount = null;
        try {
            amount = new BigDecimal(data.get("vnp_Amount")).movePointLeft(2);
        } catch (RuntimeException ignored) {
            // amount null → service trả mã 04
        }

        LocalDateTime paidAt = null;
        try {
            paidAt = LocalDateTime.parse(data.get("vnp_PayDate"), VNP_DATE);
        } catch (RuntimeException ignored) {
            // thiếu hoặc sai định dạng → dùng giờ hiện tại
        }

        boolean success = "00".equals(data.get("vnp_ResponseCode"))
                && "00".equals(data.get("vnp_TransactionStatus"));

        return new GatewayCallback(valid, data.get("vnp_TxnRef"), amount, success,
                data.get("vnp_TransactionNo"), data.get("vnp_ResponseCode"), paidAt, data);
    }

    @Override
    public RefundResult refund(Payment payment, BigDecimal amount, String requestId, String createdBy) {
        if (isBlank(props.getTmnCode()) || isBlank(props.getHashSecret())) {
            return RefundResult.failure("VNPay chưa được cấu hình");
        }

        boolean full = amount.compareTo(payment.getAmount()) == 0;
        String transactionType = full ? "02" : "03";
        String vnpAmount = toVnpAmount(amount);
        String transactionNo = payment.getGatewayCode() == null ? "" : payment.getGatewayCode();
        String transactionDate = payment.getGatewayCreateDate() != null
                ? payment.getGatewayCreateDate()
                : format(payment.getCreatedAt());
        String createDate = format(LocalDateTime.now());
        String orderInfo = "Hoan tien giao dich " + payment.getTransactionCode();

        String data = VnPayUtil.refundRequestData(requestId, "2.1.0", "refund", props.getTmnCode(),
                transactionType, payment.getTransactionCode(), vnpAmount, transactionNo, transactionDate,
                createdBy, createDate, props.getRefundIp(), orderInfo);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", props.getTmnCode());
        body.put("vnp_TransactionType", transactionType);
        body.put("vnp_TxnRef", payment.getTransactionCode());
        body.put("vnp_Amount", vnpAmount);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionNo", transactionNo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateBy", createdBy);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", props.getRefundIp());
        body.put("vnp_SecureHash", VnPayUtil.hmacSha512(props.getHashSecret(), data));

        Map<String, String> res;
        try {
            res = apiClient.postJson(props.getApiUrl(), body);
        } catch (RuntimeException e) {
            return RefundResult.failure("Không gọi được VNPay: " + e.getMessage());
        }
        if (res == null || res.isEmpty()) {
            return RefundResult.failure("VNPay không trả phản hồi");
        }

        // Chỉ tin phản hồi có chữ ký đúng
        String received = res.get("vnp_SecureHash");
        String expected = VnPayUtil.hmacSha512(props.getHashSecret(), VnPayUtil.refundResponseData(res));
        boolean signed = !isBlank(received) && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.toLowerCase().getBytes(StandardCharsets.UTF_8));

        String responseCode = res.get("vnp_ResponseCode");
        String status = res.get("vnp_TransactionStatus");
        // 00 = VNPay nhận yêu cầu, nhưng trạng thái 09 = lệnh hoàn bị từ chối. 94 = đã gửi trước đó, đang xử lý.
        boolean accepted = signed
                && (("00".equals(responseCode) && !"09".equals(status)) || "94".equals(responseCode));
        String message = signed ? res.get("vnp_Message")
                : "Phản hồi thiếu hoặc sai chữ ký (mã " + responseCode + ")";

        return new RefundResult(accepted, responseCode, status, res.get("vnp_TransactionNo"), message, res);
    }

    // VNPay yêu cầu số tiền nhân 100, không có dấu phân cách
    private String toVnpAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toPlainString();
    }

    private String format(LocalDateTime time) {
        return time.format(VNP_DATE);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
