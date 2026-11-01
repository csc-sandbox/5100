package edu.calpoly.lambdalab.paulmotter;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class RobotDistanceMessagingAdapter implements RobotDistanceLambda.MessagingAdapter {

    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;

    /**
     * Production constructor: creates default AWS clients.
     * @param SnsTopicARN SNS topic ARN
     * @param SqsURL SQS queue URL
     */
    RobotDistanceMessagingAdapter(String SnsTopicARN, String SqsURL){
        this(SnsTopicARN, SqsURL, SnsClient.create(), SqsClient.create());
    }

    /**
     * Constructor that accepts its AWS clients, so tests can supply fakes.
     * @param SnsTopicARN SNS topic ARN
     * @param SqsURL SQS queue URL
     * @param sns SNS client to use
     * @param sqs SQS client to use
     */
    RobotDistanceMessagingAdapter(String SnsTopicARN, String SqsURL, SnsClient sns, SqsClient sqs){
        this.topicArn = SnsTopicARN;
        this.queueUrl = SqsURL;
        this.sns = sns;
        this.sqs = sqs;
    }

    @Override
    public void publish(String json) {
        sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl)
            .messageBody(json).build());
    }
    
}
