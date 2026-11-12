package edu.calpoly.lambdalab.daome;

import edu.calpoly.lambdalab.daome.Exceptions.MessageException;
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
    public void publish(String id, String json) throws MessageException {
        var message = String.format("""
                {
                    "id": "%s",
                    "operation": "%s",
                    "result": %s
                }
                """, id, MovingAverageLambda.OPERATION, json);
        long snsStart = System.nanoTime();
        try {
            snsClient.publish(PublishRequest.builder().topicArn(topicArn).message(message).build());
            MovingAverageLambdaObservability.log(id, null, "SNS", "SNS_PUBLISHED", "SUCCESS", snsStart);
        } catch (Exception e) {
            MovingAverageLambdaObservability.log(id, null, "SNS", "SNS_PUBLISHED", "FAILURE", snsStart);
           throw new MessageException("Error Publishing to SNS");
        }

        long sqsStart = System.nanoTime();
        try {
            sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(sqsUrl).messageBody(message).build());
            MovingAverageLambdaObservability.log(id, null, "SQS", "SQS_SENT", "SUCCESS", sqsStart);
        } catch (Exception e) {
            MovingAverageLambdaObservability.log(id, null, "SQS", "SQS_SENT", "FAILURE", sqsStart);
            throw new MessageException("Error publishing to SQS");
        }
    }

}