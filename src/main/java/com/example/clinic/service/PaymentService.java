package com.example.clinic.service;

import com.example.clinic.dto.request.CreatePaymentRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.PaymentListItemResponse;
import com.example.clinic.dto.response.PaymentResponse;
import com.example.clinic.entity.enums.PaymentStatus;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    // Tạo giao dịch thanh toán trực tuyến cho 1 lịch hẹn PENDING, trả về URL sang cổng thanh toán
    PaymentResponse createPayment(CreatePaymentRequest request, String clientIp);

    // Xử lý IPN của VNPay, trả đúng định dạng RspCode/Message mà VNPay yêu cầu
    Map<String, String> handleVnPayIpn(Map<String, String> params);

    // FE dùng sau khi redirect về return URL để lấy trạng thái thật
    PaymentResponse getByTransactionCode(String transactionCode);

    // Id các giao dịch PENDING đã quá hạn — scheduler dùng
    List<Long> findOverduePendingPaymentIds();

    // Huỷ 1 giao dịch quá hạn (mỗi giao dịch 1 transaction riêng)
    void expireOverduePayment(Long paymentId);

    // Admin xem danh sách giao dịch (mới nhất trước), lọc theo trạng thái nếu có
    PageResponse<PaymentListItemResponse> listForAdmin(PaymentStatus status, int page, int size);
}
