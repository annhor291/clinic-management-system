package com.example.clinic.dto.response;

import com.example.clinic.config.ClinicProperties;

public record ClinicInfoResponse(
        String name, String address, String phone, String email, String openingHours,
        double latitude, double longitude,
        String mapUrl,          // mở vị trí phòng khám trên Google Maps
        String directionsUrl,   // mở chỉ đường từ vị trí hiện tại của người dùng tới phòng khám
        String embedUrl         // nhúng bản đồ vào trang bằng iframe, không cần API key
) {
    public static ClinicInfoResponse from(ClinicProperties p) {
        String point = p.getLatitude() + "," + p.getLongitude();
        return new ClinicInfoResponse(
                p.getName(), p.getAddress(), p.getPhone(), p.getEmail(), p.getOpeningHours(),
                p.getLatitude(), p.getLongitude(),
                "https://www.google.com/maps/search/?api=1&query=" + point,
                "https://www.google.com/maps/dir/?api=1&destination=" + point,
                "https://maps.google.com/maps?q=" + point + "&z=16&output=embed");
    }
}
