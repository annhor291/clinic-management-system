package com.example.clinic.payment.gateway;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VnPayUtilTest {

    // Vector kiểm thử chuẩn của RFC 4231 (test case 2, HMAC-SHA-512) — chứng minh hàm ký đúng thuật toán
    @Test
    void hmacSha512_matchesRfc4231Vector() {
        assertThat(VnPayUtil.hmacSha512("Jefe", "what do ya want for nothing?"))
                .isEqualTo("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554"
                        + "9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737");
    }

    @Test
    void buildSignedData_sortsAscending_skipsEmpty_andEncodes() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "A1");
        params.put("vnp_Amount", "100");
        params.put("vnp_OrderInfo", "Thanh toan phi kham");
        params.put("vnp_BankCode", "");
        params.put("vnp_ReturnUrl", "http://x/y");

        assertThat(VnPayUtil.buildSignedData(params)).isEqualTo(
                "vnp_Amount=100&vnp_OrderInfo=Thanh+toan+phi+kham"
                        + "&vnp_ReturnUrl=http%3A%2F%2Fx%2Fy&vnp_TxnRef=A1");
    }
}
