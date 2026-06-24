package com.pillmate.notification.infrastructure.expo;

import com.pillmate.notification.application.port.NotificationSenderPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "pillmate.notification.provider", havingValue = "expo")
public class ExpoPushSenderAdapter implements NotificationSenderPort {

    private static final String SEND_PATH = "/--/api/v2/push/send";

    private final RestClient restClient;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public ExpoPushSenderAdapter(RestClient.Builder builder,
                                 @Value("${pillmate.notification.expo.base-url:https://exp.host}") String baseUrl,
                                 MeterRegistry registry) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.sentCounter = Counter.builder("pillmate.notifications.sent")
                .tag("provider", "expo")
                .description("Expo push notifications successfully sent")
                .register(registry);
        this.failedCounter = Counter.builder("pillmate.notifications.failed")
                .tag("provider", "expo")
                .description("Expo push notifications failed to send")
                .register(registry);
    }

    @Override
    public void send(NotificationCommand command) {
        if (command.recipientPushToken() == null || command.recipientPushToken().isBlank()) {
            log.info("[EXPO-PUSH] skip — no token recipient={}", command.recipientUserId());
            return;
        }

        try {
            ExpoPushResponse response = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(command))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("[EXPO-PUSH] 4xx status={} recipient={}",
                                res.getStatusCode().value(), command.recipientUserId());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("[EXPO-PUSH] 5xx status={} recipient={}",
                                res.getStatusCode().value(), command.recipientUserId());
                    })
                    .body(ExpoPushResponse.class);

            handleResponse(command, response);
        } catch (ResourceAccessException e) {
            failedCounter.increment();
            log.error("[EXPO-PUSH] network failure recipient={} reason={}",
                    command.recipientUserId(), e.getMessage());
        }
    }

    private ExpoPushRequest toRequest(NotificationCommand cmd) {
        return new ExpoPushRequest(
                cmd.recipientPushToken(),
                cmd.title(),
                cmd.body(),
                cmd.data() == null ? Map.of() : cmd.data()
        );
    }

    private void handleResponse(NotificationCommand cmd, ExpoPushResponse response) {
        if (response == null || response.data() == null) {
            sentCounter.increment();
            return;
        }
        boolean hasError = response.data().stream()
                .anyMatch(ticket -> "error".equalsIgnoreCase(ticket.status()));
        if (hasError) {
            failedCounter.increment();
            response.data().forEach(ticket -> {
                if ("error".equalsIgnoreCase(ticket.status())) {
                    log.warn("[EXPO-PUSH] ticket error recipient={} message={}",
                            cmd.recipientUserId(), ticket.message());
                }
            });
        } else {
            sentCounter.increment();
        }
    }

    record ExpoPushRequest(String to, String title, String body, Map<String, String> data) {}

    record ExpoPushTicket(String status, String id, String message) {}

    record ExpoPushResponse(List<ExpoPushTicket> data) {}
}
