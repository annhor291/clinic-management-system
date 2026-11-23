package com.example.clinic.payment.gateway;

import com.example.clinic.entity.Payment;
import com.example.clinic.payment.config.VnPayProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VnPayGatewayServiceTest {

    private VnPayProperties props(String tmn, String secret) {
        VnPayProperties p = new VnPayProperties();
        p.setTmnCode(tmn);
        p.setHashSecret(secret);
        p.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        p.setReturnUrl("http://localhost:5173/payment/result");
        p.setPaymentExpireMinutes(15);
        return p;
    }

    private Payment payment() {
        return Payment.builder()
                .transactionCode("PAY202610191000000001")
                .amount(new BigDecimal("200000"))
                .createdAt(LocalDateTime.of(2026, 10, 19, 10, 0, 0))
                .expiredAt(LocalDateTime.of(2026, 10, 19, 10, 15, 0))
                .build();
    }

    @Test
    void buildPaymentUrl_shouldContainRequiredParamsAndValidSignature() {
        VnPayGatewayService service = new VnPayGatewayService(props("TESTCODE", "SECRET"),org.mockito.Mockito.mock(VnPayApiClient.class));

        String url = service.buildPaymentUrl(payment(), "Thanh toan phi kham APT1", "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_Amount=20000000");            // nhân 100
        assertThat(url).contains("vnp_CreateDate=20261019100000");
        assertThat(url).contains("vnp_ExpireDate=20261019101500");
        assertThat(url).contains("vnp_TxnRef=PAY202610191000000001");
        assertThat(url).contains("vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment%2Fresult");

        // Tách chữ ký ra, tính lại từ phần còn lại phải khớp
        String query = url.substring(url.indexOf('?') + 1);
        int idx = query.lastIndexOf("&vnp_SecureHash=");
        String signedData = query.substring(0, idx);
        String hash = query.substring(idx + "&vnp_SecureHash=".length());
        assertThat(hash).isEqualTo(VnPayUtil.hmacSha512("SECRET", signedData));

        // Tham số phải theo thứ tự tăng dần
        assertThat(signedData.indexOf("vnp_Amount")).isLessThan(signedData.indexOf("vnp_Command"));
    }

    @Test
    void buildPaymentUrl_shouldFailWhenNotConfigured() {
        VnPayGatewayService service = new VnPayGatewayService(props("", ""), org.mockito.Mockito.mock(VnPayApiClient.class));

        assertThatThrownBy(() -> service.buildPaymentUrl(payment(), "x", "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa được cấu hình");
    }
}
