package edu.calpoly.robot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for RobotRESTAdapter, exercised through RobotRESTProvider.
 * Replaces the manual TestRobotRESTAdapter program. Each test starts the
 * adapter on a free local port, so no running RobotServer is required.
 *
 * @author Paul Motter (PaulMotter)
 * @version 2.0.0 (10/21/2026)
 */
class RobotRESTAdapterTest {

    private static final String VALID_MESSAGE =
        "{\"jointAngles\":[2.0,1.75,1.0,0.5,0.25,0.0],"
        + "\"position\":[0.5,-10.0,5.25]}";

    private static final String WRONG_LENGTH_MESSAGE =
        "{\"jointAngles\":[1.0,2.0],\"position\":[0.0]}";

    private static final String MALFORMED_JSON = "{\"jointAngles\":[1.0,";

    private RobotBlackBoard blackboard;
    private AtomicReference<RobotMessage> lastCallback;
    private RobotRESTAdapter adapter;
    private RobotRESTProvider provider;

    @BeforeEach
    void startServer() throws Exception {
        blackboard = new RobotBlackBoard();
        lastCallback = new AtomicReference<>();
        blackboard.callbackOnPost(lastCallback::set);

        // Port 0 lets the OS pick a free port.
        adapter = new RobotRESTAdapter(0, RobotServer.ROBOT_ID, blackboard);
        adapter.run();
        provider = new RobotRESTProvider(adapter.getPort(), RobotServer.ROBOT_ID);
    }

    @AfterEach
    void stopServer() {
        adapter.shutdown();
    }

    @Test
    void getBeforeAnyDataReturns404() throws Exception {
        HttpResponse<String> response = provider.requestGET();

        assertEquals(404, response.statusCode());
        assertEquals("No robot data available.", response.body());
    }

    @Test
    void putValidMessageReturns200AndEchoesBody() throws Exception {
        HttpResponse<String> response = provider.requestPUT(VALID_MESSAGE);

        assertEquals(200, response.statusCode());
        assertEquals(VALID_MESSAGE, response.body());
    }

    @Test
    void putValidMessageUpdatesBlackboardAndNotifiesCallback() throws Exception {
        provider.requestPUT(VALID_MESSAGE);

        RobotMessage stored = blackboard.read();
        assertNotNull(stored);
        assertArrayEquals(new float[] {2.0f, 1.75f, 1.0f, 0.5f, 0.25f, 0.0f}, stored.jointAngles);
        assertArrayEquals(new float[] {0.5f, -10.0f, 5.25f}, stored.position);

        RobotMessage notified = lastCallback.get();
        assertNotNull(notified, "callback registered on the blackboard should run on PUT");
        assertArrayEquals(stored.jointAngles, notified.jointAngles);
        assertArrayEquals(stored.position, notified.position);
    }

    @Test
    void putRobotMessageObjectUpdatesBlackboard() throws Exception {
        RobotMessage message = new RobotMessage(
            new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f},
            new float[] {1.0f, 2.0f, 3.0f});

        provider.requestPUT(message);

        RobotMessage stored = blackboard.read();
        assertNotNull(stored);
        assertArrayEquals(message.jointAngles, stored.jointAngles);
        assertArrayEquals(message.position, stored.position);
    }

    @Test
    void putWrongLengthMessageReturns400AndLeavesBlackboardEmpty() throws Exception {
        HttpResponse<String> response = provider.requestPUT(WRONG_LENGTH_MESSAGE);

        assertEquals(400, response.statusCode());
        assertEquals("Invalid JSON", response.body());
        assertFalse(blackboard.hasData());
        assertNull(lastCallback.get());
    }

    @Test
    void putMalformedJsonReturns400() throws Exception {
        HttpResponse<String> response = provider.requestPUT(MALFORMED_JSON);

        assertEquals(400, response.statusCode());
        assertEquals("Invalid JSON", response.body());
        assertFalse(blackboard.hasData());
    }

    @Test
    void invalidPutDoesNotOverwritePreviousData() throws Exception {
        provider.requestPUT(VALID_MESSAGE);
        provider.requestPUT(WRONG_LENGTH_MESSAGE);

        HttpResponse<String> response = provider.requestGET();
        assertEquals(200, response.statusCode());
        assertEquals(VALID_MESSAGE, response.body());
    }

    @Test
    void getAfterPutReturnsLatestDataAsJson() throws Exception {
        provider.requestPUT(VALID_MESSAGE);

        HttpResponse<String> response = provider.requestGET();

        assertEquals(200, response.statusCode());
        assertEquals(VALID_MESSAGE, response.body());
        assertEquals("application/json",
            response.headers().firstValue("Content-Type").orElse(""));

        // The GET body must parse back into the shared RobotMessage representation.
        RobotMessage parsed = new ObjectMapper().readValue(response.body(), RobotMessage.class);
        assertArrayEquals(new float[] {0.5f, -10.0f, 5.25f}, parsed.position);
    }

    @Test
    void getReflectsMostRecentPut() throws Exception {
        provider.requestPUT(VALID_MESSAGE);
        String second = "{\"jointAngles\":[0.0,0.0,0.0,0.0,0.0,1.0],"
            + "\"position\":[1.0,1.0,1.0]}";
        provider.requestPUT(second);

        HttpResponse<String> response = provider.requestGET();

        assertEquals(200, response.statusCode());
        assertEquals(second, response.body());
    }

    @Test
    void unsupportedMethodsReturn405() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + adapter.getPort() + "/robot");

        HttpResponse<String> post = client.send(
            HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(VALID_MESSAGE)).build(),
            HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> delete = client.send(
            HttpRequest.newBuilder(uri).DELETE().build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(405, post.statusCode());
        assertEquals(405, delete.statusCode());
        assertFalse(blackboard.hasData(), "unsupported methods must not change state");
    }
}
