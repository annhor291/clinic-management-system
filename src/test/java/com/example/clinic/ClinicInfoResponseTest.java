package com.example.clinic;

import com.example.clinic.config.ClinicProperties;
import com.example.clinic.dto.response.ClinicInfoResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClinicInfoResponseTest {

    private ClinicProperties props() {
        ClinicProperties p = new ClinicProperties();
        p.setName("PK Demo");
        p.setAddress("123 Nguyen Van Linh");
        p.setLatitude(10.7292);
        p.setLongitude(106.7219);
        return p;
    }

    @Test
    void from_shouldBuildOfficialGoogleMapsUrls() {
        ClinicInfoResponse r = ClinicInfoResponse.from(props());

        assertThat(r.directionsUrl())
                .isEqualTo("https://www.google.com/maps/dir/?api=1&destination=10.7292,106.7219");
        assertThat(r.mapUrl())
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=10.7292,106.7219");
    }

    @Test
    void from_shouldCopyClinicFields() {
        ClinicInfoResponse r = ClinicInfoResponse.from(props());

        assertThat(r.name()).isEqualTo("PK Demo");
        assertThat(r.latitude()).isEqualTo(10.7292);
        assertThat(r.embedUrl()).contains("q=10.7292,106.7219").contains("output=embed");
    }
}
