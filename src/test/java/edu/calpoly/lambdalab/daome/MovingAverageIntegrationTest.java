package edu.calpoly.lambdalab.daome;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


@Tag("integration")
public class MovingAverageIntegrationTest {

    @Test
    void E2ETest() throws MqttException {
        var publisher = new MovingAverageMqttPublisher();
        var mqtt = new MovingAverageMqttClient();
        mqtt.connect();
    }
}
