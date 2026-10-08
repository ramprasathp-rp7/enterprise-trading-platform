package com.tradingplatform.notifications.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.security.AdminIdentity;
import com.tradingplatform.notifications.channel.ChannelSender;
import com.tradingplatform.notifications.channel.NotificationDispatcher;
import com.tradingplatform.notifications.channel.NotificationMessage;
import com.tradingplatform.notifications.service.AdminNotificationService;
import com.tradingplatform.preferences.AdminPreferenceResolver;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferenceResolver;

/**
 * The customer-registered admin notice, through the real service and dispatcher, with the email
 * and SMS senders mocked in place of the stubs.
 */
class AdminNotificationControllerTest {

    private static final String ADMIN = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String URL = "/api/v1/admin/notifications/customer-registered";

    private AdminPreferenceResolver resolver;
    private ChannelSender email;
    private ChannelSender sms;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resolver = mock(AdminPreferenceResolver.class);
        email = mock(ChannelSender.class);
        when(email.channel()).thenReturn(AlertChannel.EMAIL);
        sms = mock(ChannelSender.class);
        when(sms.channel()).thenReturn(AlertChannel.SMS);
        NotificationDispatcher dispatcher = new NotificationDispatcher(mock(PreferenceResolver.class), List.of(email, sms));
        AdminNotificationService service = new AdminNotificationService(resolver, dispatcher);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminNotificationController(new AdminIdentity(), service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        signIn();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendsOnTheSigningAdminsEmailAndSmsAndReportsTheChannels() throws Exception {
        when(resolver.resolveAdminAlertTargets(ADMIN)).thenReturn(List.of(
                new AlertTarget(AlertChannel.EMAIL, "ops@example.com"),
                new AlertTarget(AlertChannel.SMS, "+447700900123"),
                AlertTarget.inApp()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.customer\",\"accountId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.length()").value(3))
                .andExpect(jsonPath("$.channels[0]").value("EMAIL"))
                .andExpect(jsonPath("$.channels[1]").value("SMS"))
                .andExpect(jsonPath("$.channels[2]").value("IN_APP"));

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(email).send(eq("ops@example.com"), message.capture());
        verify(sms).send(eq("+447700900123"), any());
        org.junit.jupiter.api.Assertions.assertEquals("Customer registered", message.getValue().subject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Customer 'new.customer' was registered for account 5.", message.getValue().body());
    }

    @Test
    void inAppOnly_sendsNothingExternal() throws Exception {
        when(resolver.resolveAdminAlertTargets(ADMIN)).thenReturn(List.of(AlertTarget.inApp()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.customer\",\"accountId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[0]").value("IN_APP"));

        verify(email, never()).send(anyString(), any());
        verify(sms, never()).send(anyString(), any());
    }

    @Test
    void usernameOutsideTheRegistrationRules_isVal422AndNothingIsSent() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\\\" body=\\\"phish\",\"accountId\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));

        verify(resolver, never()).resolveAdminAlertTargets(anyString());
    }

    @Test
    void nonPositiveAccountId_isVal422() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.customer\",\"accountId\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void noVerifiedToken_isAuth401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.customer\",\"accountId\":5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    private static void signIn() {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of("sub", ADMIN, "accountId", 0, "roles", List.of("ADMIN"), "iss", "auth-service"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
