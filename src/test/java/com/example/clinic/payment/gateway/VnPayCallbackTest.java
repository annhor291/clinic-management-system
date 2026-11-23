package com.example.clinic.payment.gateway;

import com.example.clinic.payment.config.VnPayProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VnPayCallbackTest {

    private static final String SECRET = "SECRET";

    private VnPayGatewayService service(String secret) {
        VnPayProperties p = new VnPayProperties();
        p.setTmnCode("TESTCODE");
        p.setHashSecret(secret);
        p.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        p.setReturnUrl("http://localhost:5173/payment/result");
        return new VnPayGatewayService(p,org.mockito.Mockito.mock(VnPayApiClient.class));
    }

    private Map<String, String> data() {
        Map<String, String> d = new HashMap<>();
        d.put("vnp_TmnCode", "TESTCODE");
        d.put("vnp_TxnRef", "PAY202610201000000001");
        d.put("vnp_Amount", "20000000");
        d.put("vnp_OrderInfo", "Thanh toan phi kham APT1");
        d.put("vnp_ResponseCode", "00");
        d.put("vnp_TransactionStatus", "00");
        d.put("vnp_TransactionNo", "14123456");
        d.put("vnp_PayDate", "20261020103000");
        return d;
    }

    private Map<String, String> sign(Map<String, String> d) {
        Map<String, String> m = new HashMap<>(d);
        m.put("vnp_SecureHash", VnPayUtil.hmacSha512(SECRET, VnPayUtil.buildSignedData(d)));
        return m;
    }

    @Test
    void validCallback_shouldBeParsed() {
        GatewayCallback cb = service(SECRET).parseCallback(sign(data()));

        assertThat(cb.signatureValid()).isTrue();
        assertThat(cb.success()).isTrue();
        assertThat(cb.transactionCode()).isEqualTo("PAY202610201000000001");
        assertThat(cb.amount()).isEqualByComparingTo("200000");   // đã chia 100
        assertThat(cb.gatewayTransactionNo()).isEqualTo("14123456");
        assertThat(cb.paidAt()).isNotNull();
    }

    @Test
    void secureHashType_shouldBeExcludedFromSignature() {
        Map<String, String> params = sign(data());
        params.put("vnp_SecureHashType", "HmacSHA512");

        assertThat(service(SECRET).parseCallback(params).signatureValid()).isTrue();
    }

    @Test
    void tamperedAmount_shouldFailSignature() {
        Map<String, String> params = sign(data());
        params.put("vnp_Amount", "100");

        assertThat(service(SECRET).parseCallback(params).signatureValid()).isFalse();
    }

    @Test
    void missingHash_orBlankSecret_shouldFailSignature() {
        assertThat(service(SECRET).parseCallback(data()).signatureValid()).isFalse();
        assertThat(service("").parseCallback(sign(data())).signatureValid()).isFalse();
    }

    @Test
    void failedResponseCode_shouldNotBeSuccess() {
        Map<String, String> d = data();
        d.put("vnp_ResponseCode", "24");
        d.put("vnp_TransactionStatus", "02");

        GatewayCallback cb = service(SECRET).parseCallback(sign(d));

        assertThat(cb.signatureValid()).isTrue();
        assertThat(cb.success()).isFalse();
    }
}
