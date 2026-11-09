package com.travelmapper.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmapper.common.security.RequestContext;
import com.travelmapper.common.security.RequestIdentity;
import com.travelmapper.repository.entity.AuditLog;
import com.travelmapper.repository.mapper.AuditLogMapper;
import com.travelmapper.repository.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuditLogService service =
            new AuditLogService(mapper, userMapper, new ObjectMapper());

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        RequestContext.clear();
    }

    @Test
    void writeFailureIsSwallowed() {
        when(mapper.insert(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.record(1L, "USER_PROFILE_UPDATE", "USER", 1L,
                Map.of("changed", "nickname"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void requestIdentityRoleAndForwardedIpAreUsed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestContext.set(new RequestIdentity(7L, "USER"));
        when(mapper.insert(any(AuditLog.class))).thenReturn(1);

        service.record(7L, "NOTE_CREATE", "NOTE", 1L, Map.of(), null);

        AuditLog entry = capture();
        assertThat(entry.getActorRole()).isEqualTo("USER");
        assertThat(entry.getIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void anonymousRequestAndSystemWriteAreDistinguished() {
        when(mapper.insert(any(AuditLog.class))).thenReturn(1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestContext.set(RequestIdentity.ANONYMOUS);
        service.record(null, "NOTE_REPORT", "NOTE", 1L, Map.of(), null);
        assertThat(capture().getActorRole()).isEqualTo("ANONYMOUS");

        RequestContextHolder.resetRequestAttributes();
        RequestContext.clear();
        service.record(null, "SYSTEM_TASK", "CONFIG", null, Map.of(), null);
        assertThat(capture().getActorRole()).isEqualTo("SYSTEM");
    }

    @Test
    void userRoleFallsBackToUserTableOutsideRequest() {
        com.travelmapper.repository.entity.User user =
                new com.travelmapper.repository.entity.User();
        user.setId(9L);
        user.setRole(com.travelmapper.repository.enums.Role.ADMIN);
        when(userMapper.selectById(9L)).thenReturn(user);
        when(mapper.insert(any(AuditLog.class))).thenReturn(1);

        service.record(9L, "USER_VIP_SET", "USER", 9L, Map.of(), null);

        assertThat(capture().getActorRole()).isEqualTo("ADMIN");
    }

    private AuditLog capture() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper, atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }
}