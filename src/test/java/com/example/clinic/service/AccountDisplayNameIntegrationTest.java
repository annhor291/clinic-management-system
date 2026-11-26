package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountDisplayNameIntegrationTest extends BaseIntegrationTest {

    @Autowired private PatientService patientService;
    @Autowired private UserRepository userRepository;

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .username(email).email(email).password("x")
                .role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private String usernameOf(User u) {
        return userRepository.findById(u.getId()).orElseThrow().getDisplayName();
    }

    private PatientCreateRequest createReq(String fullName) {
        PatientCreateRequest r = new PatientCreateRequest();
        r.setFullName(fullName);
        return r;
    }

    private PatientUpdateRequest updateReq(String fullName) {
        PatientUpdateRequest r = new PatientUpdateRequest();
        r.setFullName(fullName);
        return r;
    }

    @Test
    void completingProfile_setsAccountNameToFullName_andUpdateKeepsItInSync() {
        User u = newUser("p1@example.com");

        PatientResponse p = patientService.create(u.getId(), createReq("Nguyễn Văn A"));
        assertThat(usernameOf(u)).isEqualTo("Nguyễn Văn A");

        patientService.update(p.getId(), updateReq("Nguyễn Văn B"));
        assertThat(usernameOf(u)).isEqualTo("Nguyễn Văn B");
    }

    @Test
    void twoAccounts_canHaveTheSameFullName() {
        User a = newUser("a@example.com");
        User b = newUser("b@example.com");

        patientService.create(a.getId(), createReq("Trần Thị C"));
        patientService.create(b.getId(), createReq("Trần Thị C"));

        assertThat(usernameOf(a)).isEqualTo("Trần Thị C");
        assertThat(usernameOf(b)).isEqualTo("Trần Thị C");
    }
}
