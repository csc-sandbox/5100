package edu.calpoly.lambdalab.paulmotter;

import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.calpoly.lambdalab.paulmotter.RobotDistanceObservability.Status;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes the result event to SNS, then sends it to SQS.
 * Each call is retried on its own, so an SQS retry never re-publishes to SNS.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.1 (2026-11-10)
 */
public class RobotDistanceMessagingAdapter implements MessagingAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SnsClient sns;
    private final SqsClient sqs;
    private final String topicArn;
    private final String queueUrl;
    private final RetryExecutor retry;

    /**
     * Production constructor: creates AWS clients with SDK retries turned off,
     * so the RetryExecutor is the only retry layer.
     * @param SnsTopicARN SNS topic ARN
     * @param SqsURL SQS queue URL
     */
    RobotDistanceMessagingAdapter(String SnsTopicARN, String SqsURL){
        this(SnsTopicARN, SqsURL,
            SnsClient.builder().overrideConfiguration(RetryExecutor.withoutSdkRetries()).build(),
            SqsClient.builder().overrideConfiguration(RetryExecutor.withoutSdkRetries()).build());
    }

    /**
     * Constructor that accepts its AWS clients and uses the default retry policy.
     * @param SnsTopicARN SNS topic ARN
     * @param SqsURL SQS queue URL
     * @param sns SNS client to use
     * @param sqs SQS client to use
     */
    RobotDistanceMessagingAdapter(String SnsTopicARN, String SqsURL, SnsClient sns, SqsClient sqs){
        this(SnsTopicARN, SqsURL, sns, sqs, RetryExecutor.forAws());
    }

    /**
     * Constructor that accepts its AWS clients and retry policy, so tests can supply fakes.
     * @param SnsTopicARN SNS topic ARN
     * @param SqsURL SQS queue URL
     * @param sns SNS client to use
     * @param sqs SQS client to use
     * @param retry retry policy applied to each AWS call
     */
    RobotDistanceMessagingAdapter(String SnsTopicARN, String SqsURL, SnsClient sns, SqsClient sqs,
                                  RetryExecutor retry){
        this.topicArn = SnsTopicARN;
        this.queueUrl = SqsURL;
        this.sns = sns;
        this.sqs = sqs;
        this.retry = retry;
    }

    @Override
    public void publish(String json) {
        String id = readId(json);

        // SNS
        final long snsStart = System.nanoTime();
        try {
            retry.execute(
                () -> sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build()),
                failure -> RobotDistanceObservability.log(id, null, "SNS", "PUBLISH RETRY", Status.FAILURE, snsStart, failure));
            RobotDistanceObservability.log(id, null, "SNS", "PUBLISHED", Status.SUCCESS, snsStart);
        }
        catch (RuntimeException exception) {
            RobotDistanceObservability.log(id, null, "SNS", "PUBLISHED", Status.FAILURE, snsStart, exception);
            throw exception;
        }

        // SQS
        final long sqsStart = System.nanoTime();
        try {
            retry.execute(
                () -> sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build()),
                failure -> RobotDistanceObservability.log(id, null, "SQS", "QUEUE RETRY", Status.FAILURE, sqsStart, failure));
            RobotDistanceObservability.log(id, null, "SQS", "QUEUED", Status.SUCCESS, sqsStart);
        }
        catch (RuntimeException exception) {
            RobotDistanceObservability.log(id, null, "SQS", "QUEUED", Status.FAILURE, sqsStart, exception);
            throw exception;
        }
    }

    /** Reads the event id for telemetry; returns null if the JSON has none. */
    private static String readId(String json) {
        try {
            return MAPPER.readTree(json).path("id").asText(null);
        } catch (Exception ignore) {
            return null;
        }
    }
}
