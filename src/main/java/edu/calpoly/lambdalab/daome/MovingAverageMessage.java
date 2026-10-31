package edu.calpoly.lambdalab.daome;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class MovingAverageMessage implements MovingAverageLambda.Message {
    private final SqsClient sqsClient = SqsClient.create();
    private final SnsClient snsClient = SnsClient.create();
    private final String topicArn;
    private final String sqsUrl;

    public MovingAverageMessage(String topicArn, String sqsUrl) {
        this.topicArn = topicArn;
        this.sqsUrl = sqsUrl;
    }

    @Override
    public void publish(String id, String json) {

        var message = String.format("""
                {
                    "id": "%s",
                    "operation": "%s",
                    "result": %s
                }
                """, id, MovingAverageLambda.OPERATION, json);

        snsClient.publish(PublishRequest.builder().topicArn(topicArn).message(message).build());
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(sqsUrl).messageBody(message).build());
    }

}