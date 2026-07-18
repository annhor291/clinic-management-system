package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatisticsResponse {

    private long totalUsers;
    private long totalActive;
    private long totalInactive;
    private long totalLocked;
    private Map<Role, Long> countByRole;
}
