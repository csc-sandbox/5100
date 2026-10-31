package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.EventStatus;
import edu.calpoly.lambdalab.daome.data.MovingAverageData;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEvent;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEventResponse;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Tag("integration")
public class MovingAverageIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String topic = MovingAverageMqttClient.EVENT_TOPIC;
    private final ConcurrentHashMap<UUID, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, MovingAverageLambdaEventResponse> acks = new ConcurrentHashMap<>();
    private final String sampleData2 = """
                {
                "id": "hello",
                "operation": "MovingAverageLambda",
                "result":
                     {
                        "id": "hello",
                        "samples": [-1, -2, 3, 4, 5],
                        "window": "-1"
                        "MovingAverage":  [2, 7,4]
                    }
                }
            """;
    private MovingAverageMqttClient desktopClient;
    private MqttClient testClient;

    @BeforeEach
    void setup() throws Exception {
        desktopClient = new MovingAverageMqttClient();
        desktopClient.connect();

        testClient =
                new MqttClient(MovingAverageMqttClient.BROKER, MqttClient.generateClientId(), new MemoryPersistence());
        testClient.connect();
        // subscribe with a listener lambda; push each ack payload into the queue
        testClient.subscribe(MovingAverageMqttClient.ACK_TOPIC, 1, (topic, msg) -> {
            var json = mapper.readValue(msg.getPayload(), MovingAverageLambdaEventResponse.class);
            acks.put(json.id(), json);
            latchFor(json.id()).countDown();
        });

    }

    private void publish(Object event) throws Exception {
        testClient.publish(topic, new MqttMessage(mapper.writeValueAsBytes(event)));
    }


    private CountDownLatch latchFor(UUID id) {
        return latches.computeIfAbsent(id, k -> new CountDownLatch(1));
    }

    @AfterEach
    void shutdown() throws Exception {
        desktopClient.close();
        testClient.disconnect();
        testClient.close();
    }

    @Test
    void validAck() throws Exception {
        UUID id = UUID.randomUUID();
        MovingAverageData sampleData1 = new MovingAverageData(id, List.of(3, 5, 7, 9), 2, List.of(4.0, 6.0, 8.0));

        publish(new MovingAverageLambdaEvent(sampleData1.id(), MovingAverageLambda.OPERATION, sampleData1));
        var latch = latchFor(id);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "no acks within 5s");
        assertEquals(EventStatus.SUCCESS, acks.get(id).status());
    }


    @Test
    void missingResultGetsErrorAck() throws Exception {
        UUID id = UUID.randomUUID();
        var latch = latchFor(id);

        publish(new MovingAverageLambdaEvent(id, MovingAverageLambda.OPERATION, null));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "no ack within 5s");
        assertEquals(EventStatus.ERROR, acks.get(id).status());
    }


    @Test
    void failThenPass() throws Exception {
        missingResultGetsErrorAck();
        validAck();
    }
}


