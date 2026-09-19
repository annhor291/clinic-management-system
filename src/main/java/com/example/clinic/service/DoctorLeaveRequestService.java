package com.example.clinic.service;

import com.example.clinic.dto.request.LeaveRejectRequest;
import com.example.clinic.dto.request.LeaveRequestSubmitRequest;
import com.example.clinic.dto.response.LeaveRequestResponse;
import com.example.clinic.dto.response.PageResponse;

public interface DoctorLeaveRequestService {

    LeaveRequestResponse submit(LeaveRequestSubmitRequest request);

    LeaveRequestResponse getById(Long id);

    PageResponse<LeaveRequestResponse> getMyLeaveRequests(int page, int size);

    PageResponse<LeaveRequestResponse> getByDoctor(Long doctorId, int page, int size);

    PageResponse<LeaveRequestResponse> getPending(int page, int size);

    LeaveRequestResponse approve(Long id);

    LeaveRequestResponse reject(Long id, LeaveRejectRequest request);
}
