# A04 — Gaze Cluster Reliability Evidence

## CloudWatch evidence

After deployment, invoke `POST /5100/gaze-clusters` and capture the public
CSC5100-Observability dashboard entry for `GazeClusterLambda`. If the dashboard
does not expose the Lambda or its log group, state that limitation in the PR and
include the deployment region and invocation time. Application events are JSON,
for example:

```json
{"event":"gaze_cluster.persisted","operation":"GazeClusterLambda","id":"<uuid>"}
```

## Interfaces and dependency injection

`GazeClusterLambda` depends on the `Storage`, `Messaging`, and `EventLogger`
interfaces. Production adapters use DynamoDB/S3, SNS/SQS, and the Lambda logger;
tests supply fake implementations. `GazeClusterLambdaTest.postPersistsAndPublishesOneCorrelatedResult`
uses fake storage and messaging, while `GazeClusterReliabilityTest` supplies a
deterministic failing storage dependency.

## Structured logging and failure behavior

`GazeClusterStructuredLogger` writes one JSON object per event and never changes
the outcome of a business operation if logging fails. Events cover acceptance,
persistence, fan-out, lookup, rejected input, and operation failure.

The persistence step has three bounded attempts with 25 ms then 50 ms backoff.
`GazeClusterReliabilityTest.retriesIdempotentStorageAndRecovers` verifies recovery,
and `returns500AfterBoundedRetryExhaustion` verifies that three failed attempts
produce a 500 response.

## CloudWatch errors and idempotency

An application failure is a handled response, such as a malformed request (400)
or exhausted persistence retry (500), accompanied by a structured log event.
A Lambda execution error is an unhandled runtime or platform failure recorded by
the Lambda service; it can cause Lambda-level retries or error metrics even when
no application response is produced.

Only persistence is retried. Its DynamoDB record and S3 object use the same UUID
and fixed key, so repeating the write is safe. SNS/SQS publishing is not retried
inside this Lambda because a timeout can occur after a broker accepts a message;
blind retrying could publish duplicates. Consumers must therefore treat the UUID
as their idempotency key.

## Test evidence

Run from the repository root:

```cmd
mvn "-Dtest=edu.calpoly.lambdalab.jjuangon03.GazeClusterLambdaTest,edu.calpoly.lambdalab.jjuangon03.GazeClusterMqttSubscriberTest,edu.calpoly.lambdalab.jjuangon03.GazeClusterReliabilityTest" test
```

The existing A04 MQTT integration test remains opt-in:

```cmd
mvn "-Dtest=edu.calpoly.lambdalab.jjuangon03.GazeClusterMqttLiveIT" "-Dhivemq.integration=true" test
```
