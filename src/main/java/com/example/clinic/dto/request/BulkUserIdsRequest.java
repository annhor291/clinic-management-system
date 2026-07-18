package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Dùng cho các thao tác hàng loạt: kích hoạt/vô hiệu hoá/xoá nhiều user cùng lúc
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUserIdsRequest {

    @NotEmpty(message = "Danh sách id không được để trống")
    private List<Long> ids;
}
