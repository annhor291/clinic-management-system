package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueStatusResponse {

    // Số thứ tự đang được khám hiện tại trong ca này — null nếu ca chưa bắt đầu khám ai
    private Integer currentServingNumber;

    // Tổng số slot trong ca (để FE biết "hiện đang khám X/Y")
    private Integer totalInShift;

    // Ca có đang trễ so với giờ dự kiến không — true khi đã qua giờ bắt đầu của slot kế tiếp
    // cần khám mà chưa có ai hoàn thành/no-show tương ứng
    private boolean delayed;

    private String delayMessage;
}
