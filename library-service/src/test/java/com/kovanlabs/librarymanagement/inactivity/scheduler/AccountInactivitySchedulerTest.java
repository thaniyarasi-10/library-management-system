package com.kovanlabs.librarymanagement.inactivity.scheduler;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.inactivity.service.AccountInactivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountInactivitySchedulerTest {

    private UserRepository userRepository;
    private AccountInactivityService inactivityService;
    private AccountInactivityScheduler scheduler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        inactivityService = mock(AccountInactivityService.class);

        scheduler = new AccountInactivityScheduler(
                userRepository,
                inactivityService
        );
    }

    @Test
    @DisplayName("Scheduled execution processes active users in batches across pagination")
    void processAccountInactivity_batchProcessing() {
        User u1 = User.builder().id(1L).email("u1@test.com").status(AccountStatus.ACTIVE).build();
        User u2 = User.builder().id(2L).email("u2@test.com").status(AccountStatus.ACTIVE).build();

        PageImpl<User> page0 = new PageImpl<>(List.of(u1, u2), PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE), 2);

        when(userRepository.findByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE))))
                .thenReturn(page0);

        scheduler.processAccountInactivity();

        verify(userRepository).findByStatus(AccountStatus.ACTIVE, PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE));
        verify(inactivityService).processUser(u1);
        verify(inactivityService).processUser(u2);
    }

    @Test
    @DisplayName("Individual user exception should be caught and not abort remaining users")
    void processAccountInactivity_userExceptionIsolated() {
        User u1 = User.builder().id(1L).email("u1@test.com").status(AccountStatus.ACTIVE).build();
        User u2 = User.builder().id(2L).email("u2@test.com").status(AccountStatus.ACTIVE).build();

        PageImpl<User> page0 = new PageImpl<>(List.of(u1, u2), PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE), 2);
        when(userRepository.findByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE))))
                .thenReturn(page0);

        doThrow(new RuntimeException("DB glitch")).when(inactivityService).processUser(u1);

        scheduler.processAccountInactivity();

        verify(inactivityService).processUser(u1);
        verify(inactivityService).processUser(u2);
    }

    @Test
    @DisplayName("Concurrent execution should be skipped if already running")
    void processAccountInactivity_concurrentExecutionSkipped() throws Exception {
        Field field = AccountInactivityScheduler.class.getDeclaredField("isRunning");
        field.setAccessible(true);
        AtomicBoolean isRunning = (AtomicBoolean) field.get(scheduler);
        isRunning.set(true);

        scheduler.processAccountInactivity();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(inactivityService);
    }

    @Test
    @DisplayName("Empty page terminates loop")
    void processAccountInactivity_emptyPageTerminates() {
        PageImpl<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE), 0);
        when(userRepository.findByStatus(eq(AccountStatus.ACTIVE), eq(PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE))))
                .thenReturn(emptyPage);

        scheduler.processAccountInactivity();

        verify(userRepository).findByStatus(AccountStatus.ACTIVE, PageRequest.of(0, AccountInactivityScheduler.BATCH_SIZE));
        verifyNoInteractions(inactivityService);
    }
}
