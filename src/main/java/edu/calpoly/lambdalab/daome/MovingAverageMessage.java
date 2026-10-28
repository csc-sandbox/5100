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
    public void publishSns(String json) {
        snsClient.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
    }

    @Override
    public void publishSqs(String json) {
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(sqsUrl).messageBody(json).build());
    }
}