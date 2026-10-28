package edu.calpoly.lambdalab.ejiswkrosmew;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Messaging class to send events to both SQS and SNS
 * Heavily based on edu.calpoly.lambdalab.instructor.TemperatureMessaging
 *
 * @author James Yaguma
 * @version 1.0 (2026-10-27)
 */
public class LidarSafetyMessaging implements LidarSafetyLambda.Messaging {
    private static final String OPERATION = "LidarSafetyLambda";
    private final SnsClient sns = SnsClient.create();
    private final SqsClient sqs = SqsClient.create();
    private final String topicArn;
    private final String queueUrl;

    LidarSafetyMessaging(String topicArn, String queueUrl) {
        this.topicArn = topicArn;
        this.queueUrl = queueUrl;
    }

    /**
     * Publish a result String in JSON format to both SNS and SQS
     *
     * @param json The result of a query as a String in JSON format
     */
    @Override
    public void publish(String json) {
        String id = json.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String eventJson = "{\"id\":\"" + id + "\", \"operation\":\"" + OPERATION + "\", \"result\":" + json + "}";

        // SNS publish
        PublishRequest pubReq = PublishRequest.builder()
            .topicArn(topicArn)
            .message(eventJson)
            .build();
        sns.publish(pubReq);

        // SQS send message
        SendMessageRequest sendMsgReq = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(eventJson)
            .build();
        sqs.sendMessage(sendMsgReq);
    }
}
