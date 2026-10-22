package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.UserPaymentProfile;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;

public interface UserPaymentProfileService {

    UserPaymentProfileResponseDto getProfile(String email);

    UserPaymentProfileResponseDto saveOrUpdateProfile(UserPaymentProfileRequestDto request, String email);

    void deleteProfile(String email);

    UserPaymentProfile findByUserUuid(String userUuid);
}
