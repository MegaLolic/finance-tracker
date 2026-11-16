package org.portfolio.financeservice.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.portfolio.financeservice.exception.ResourceNotFoundException;
import org.portfolio.financeservice.exception.UserServiceException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UserClientTest {
    private HttpServer server;
    private UserClient client;
    private int status = 200;
    private String body = "{\"id\":1,\"name\":\"Test\",\"surname\":\"User\",\"email\":\"test@example.com\"}";
    private long delayMs;
    private long bodyDelayMs;
    private String contentType = "application/json";
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/1", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    if (bodyDelayMs > 0) {
                        exchange.getResponseBody().write(bytes, 0, 1);
                        exchange.getResponseBody().flush();
                        Thread.sleep(bodyDelayMs);
                        exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                    } else {
                        exchange.getResponseBody().write(bytes);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = clientWithTimeout(Duration.ofSeconds(2));
    }

    private UserClient clientWithTimeout(Duration timeout) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        var builder = WebClient.builder().clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(timeout)));
        var configured = new UserClient(builder, request);
        ReflectionTestUtils.setField(configured, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        return configured;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void successForwardsAuthenticationAndReadsUser() {
        assertEquals(1L, client.getUserById(1L).getId());
        assertEquals("Bearer test-token", authorization.get());
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "400,502", "429,502", "500,502", "503,503", "504,504"})
    void upstreamErrorsKeepPublicStatusWithoutLeakingBody(int upstreamStatus, int expectedStatus) {
        status = upstreamStatus;
        body = "private upstream details";
        var failure = assertThrows(UserServiceException.class, () -> client.getUserById(1L));
        assertEquals(expectedStatus, failure.getStatus().value());
        assertFalse(failure.getMessage().contains(body));
    }

    @Test
    void empty404StillMapsToNotFound() {
        status = 404;
        body = "";
        assertThrows(ResourceNotFoundException.class, () -> client.getUserById(1L));
    }

    @Test
    void emptySuccessMapsToBadGateway() {
        body = "";
        assertEquals(502, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @Test
    void invalidJsonMapsToBadGateway() {
        body = "not-json";
        assertEquals(502, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":2}"})
    void missingOrWrongUserIdMapsToBadGateway(String response) {
        body = response;
        assertEquals(502, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @Test
    void unsupportedResponseContentTypeMapsToBadGateway() {
        contentType = "application/octet-stream";
        assertEquals(502, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @Test
    void timeoutReadingSuccessfulResponseBodyMapsToGatewayTimeout() {
        bodyDelayMs = 400;
        client = clientWithTimeout(Duration.ofMillis(150));
        assertEquals(504, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @Test
    void refusedConnectionMapsToUnavailable() {
        server.stop(0);
        assertEquals(503, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }

    @Test
    void readTimeoutMapsToGatewayTimeout() {
        delayMs = 400;
        client = clientWithTimeout(Duration.ofMillis(150));
        assertEquals(504, assertThrows(UserServiceException.class, () -> client.getUserById(1L)).getStatus().value());
    }
}
