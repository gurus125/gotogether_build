package com.gotogether.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.auth.config.TwilioProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwilioOtpSenderTest {

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    private TwilioProperties properties;
    private TwilioOtpSender sender;

    @BeforeEach
    void setUp() {
        properties = new TwilioProperties();
        properties.setAccountSid("ACtest123");
        properties.setAuthToken("secret-token");
        properties.setFromNumber("+15551234567");
        sender = new TwilioOtpSender(properties, httpClient);
    }

    @Test
    void postsToTheAccountsSpecificTwilioMessagesEndpointWithBasicAuth() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        sender.send("+919876543210", "123456");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();

        assertThat(sent.uri().toString()).isEqualTo("https://api.twilio.com/2010-04-01/Accounts/ACtest123/Messages.json");
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/x-www-form-urlencoded");
        // Basic base64("ACtest123:secret-token")
        String expectedAuth = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("ACtest123:secret-token".getBytes(StandardCharsets.UTF_8));
        assertThat(sent.headers().firstValue("Authorization")).contains(expectedAuth);
    }

    @Test
    void includesTheDestinationNumberFromNumberAndCodeInTheRequestBody() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        sender.send("+919876543210", "654321");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String body = bodyAsString(captor.getValue());

        assertThat(body).contains("To=%2B919876543210");
        assertThat(body).contains("From=%2B15551234567");
        assertThat(body).contains("654321");
    }

    @Test
    void doesNotThrowWhenTwilioReturnsANonSuccessStatus() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"message\": \"invalid number\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThatCode(() -> sender.send("+919876543210", "123456")).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowWhenTheHttpCallItselfFails() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        assertThatCode(() -> sender.send("+919876543210", "123456")).doesNotThrowAnyException();
    }

    private static String bodyAsString(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        request.bodyPublisher().ifPresent(publisher -> publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        }));
        return sb.toString();
    }
}
