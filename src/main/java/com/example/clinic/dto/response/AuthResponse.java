package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
// DTO trả về sau khi đăng nhập / đăng ký thành công
public class AuthResponse {

    // JWT token để client dùng cho các request tiếp theo
    private String token;

    // Thông tin user
    private Long userId;
    private String username;
    private String email;
    private Role role;

    // ID của Patient hoặc Doctor tương ứng (nếu có)
    // Dùng để client biết profileId để gọi API lấy thông tin chi tiết
    private Long profileId;
}
