package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserPaymentProfile;
import com.kovanlabs.librarymanagement.database.repository.UserPaymentProfileRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;
import com.kovanlabs.librarymanagement.payment.mapping.UserPaymentProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPaymentProfileServiceImpl implements UserPaymentProfileService {

    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT_NUM_PATTERN = Pattern.compile("^\\d{9,30}$");

    private final UserPaymentProfileRepository paymentProfileRepository;
    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;

    private User findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User identity is required");
        }
        return userProviderRepository.findByProviderId(identifier)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + identifier));
    }

    @Override
    @Transactional(readOnly = true)
    public UserPaymentProfileResponseDto getProfile(String email) {
        var user = findUserByIdentifier(email);
        var profile = paymentProfileRepository.findByUserUuid(user.getUuid()).orElse(null);
        return UserPaymentProfileMapper.INSTANCE.mapToResponse(profile);
    }

    @Override
    @Transactional
    public UserPaymentProfileResponseDto saveOrUpdateProfile(UserPaymentProfileRequestDto request, String email) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment profile request cannot be null");
        }

        var user = findUserByIdentifier(email);
        var cleanIfsc = validateAndFormatIfsc(request.ifscCode());
        var cleanAccount = validateAndFormatAccountNumber(request.accountNumber());
        var cleanBank = request.bankName().trim();
        var cleanHolder = request.accountHolderName().trim();

        if (cleanHolder.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account holder name cannot be empty");
        }

        var existingProfile = paymentProfileRepository.findByUserUuid(user.getUuid()).orElse(null);

        UserPaymentProfile profileToSave;
        if (existingProfile != null) {
            UserPaymentProfileMapper.INSTANCE.updateEntityFromDto(request, existingProfile);
            profileToSave = existingProfile;
        } else {
            profileToSave = UserPaymentProfileMapper.INSTANCE.mapToEntity(request);
            profileToSave.setUserUuid(user.getUuid());
        }

        // Set validated/sanitized values
        profileToSave.setBankName(cleanBank);
        profileToSave.setAccountNumber(cleanAccount);
        profileToSave.setIfscCode(cleanIfsc);
        profileToSave.setAccountHolderName(cleanHolder);

        var saved = paymentProfileRepository.save(profileToSave);
        log.info("Saved bank account profile for userUuid: {}", user.getUuid());
        return UserPaymentProfileMapper.INSTANCE.mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProfile(String email) {
        var user = findUserByIdentifier(email);
        paymentProfileRepository.findByUserUuid(user.getUuid()).ifPresent(profile -> {
            paymentProfileRepository.delete(profile);
            log.info("Deleted bank account profile for userUuid: {}", user.getUuid());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UserPaymentProfile findByUserUuid(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            return null;
        }
        return paymentProfileRepository.findByUserUuid(userUuid).orElse(null);
    }

    private String validateAndFormatIfsc(String ifscCode) {
        if (ifscCode == null || ifscCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IFSC code is required");
        }
        var formatted = ifscCode.trim().toUpperCase();
        if (!IFSC_PATTERN.matcher(formatted).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IFSC code format: must be 11 characters (e.g. HDFC0001234)");
        }
        return formatted;
    }

    private String validateAndFormatAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account number is required");
        }
        var formatted = accountNumber.trim().replaceAll("\\s+", "");
        if (!ACCOUNT_NUM_PATTERN.matcher(formatted).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid bank account number: must be 9 to 30 digits");
        }
        return formatted;
    }
}
