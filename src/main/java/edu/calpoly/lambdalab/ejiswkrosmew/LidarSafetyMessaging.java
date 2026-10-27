package edu.calpoly.lambdalab.ejiswkrosmew;

/**
 * Messaging class to send events to both SQS and SNS
 * Heavily based on edu.calpoly.lambdalab.instructor.TemperatureMessaging
 *
 * @author James Yaguma
 * @version 1.0 (2026-10-27)
 */
public class LidarSafetyMessaging implements LidarSafetyLambda.Messaging {
    private final String topicArn;
    private final String queueUrl;

    LidarSafetyMessaging(String topicArn, String queueUrl) {
        this.topicArn = topicArn;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publish(String json) {

    }
}
