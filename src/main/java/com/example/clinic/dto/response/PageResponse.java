package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T>{
    // Danh sách dữ liệu của trang hiện tại
    private List<T> content;

    // Trang hiện tại (bắt đầu từ 0)
    private int pageNumber;

    // Số phần tử mỗi trang
    private int pageSize;

    // Tổng số phần tử
    private long totalElements;

    // Tổng số trang
    private int totalPages;

    // Có phải trang cuối không
    private boolean last;

    // Convert từ Spring Page object sang PageResponse
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
