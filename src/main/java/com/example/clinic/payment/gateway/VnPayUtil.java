package com.example.clinic.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Tiện ích ký chữ ký cho VNPay. Tách riêng, không phụ thuộc Spring, để unit test được
// và dùng lại được ở Bước 3 (xác thực IPN) và Bước 5 (hoàn tiền).
public final class VnPayUtil {

    private VnPayUtil() {
    }

    // Sắp xếp tăng dần theo tên tham số, bỏ tham số rỗng, mã hoá URL, nối bằng '&'.
    // Chuỗi này vừa là dữ liệu để ký, vừa là query string của URL thanh toán.
    public static String buildSignedData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký HMAC-SHA512", e);
        }
    }

    // Giá trị gửi sang VNPay đều là ASCII (OrderInfo không dấu), nên US_ASCII giống code mẫu của VNPay
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    // Chuỗi ký của yêu cầu refund: đúng thứ tự trong tài liệu VNPay
    public static String refundRequestData(String requestId, String version, String command, String tmnCode,
                                           String transactionType, String txnRef, String amount,
                                           String transactionNo, String transactionDate, String createBy,
                                           String createDate, String ipAddr, String orderInfo) {
        return String.join("|", nz(requestId), nz(version), nz(command), nz(tmnCode), nz(transactionType),
                nz(txnRef), nz(amount), nz(transactionNo), nz(transactionDate), nz(createBy),
                nz(createDate), nz(ipAddr), nz(orderInfo));
    }

    // Chuỗi ký của phản hồi refund. Trường thiếu được tính là chuỗi rỗng.
    public static String refundResponseData(Map<String, String> r) {
        return String.join("|",
                nz(r.get("vnp_ResponseId")), nz(r.get("vnp_Command")), nz(r.get("vnp_ResponseCode")),
                nz(r.get("vnp_Message")), nz(r.get("vnp_TmnCode")), nz(r.get("vnp_TxnRef")),
                nz(r.get("vnp_Amount")), nz(r.get("vnp_BankCode")), nz(r.get("vnp_PayDate")),
                nz(r.get("vnp_TransactionNo")), nz(r.get("vnp_TransactionType")),
                nz(r.get("vnp_TransactionStatus")), nz(r.get("vnp_OrderInfo")));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
