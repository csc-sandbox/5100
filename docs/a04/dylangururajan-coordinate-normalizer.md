# A04 — Coordinate Normalizer

This document describes the local implementation and the evidence to collect after the shared AWS
resources deploy it. It intentionally contains no credentials, certificates, or synthetic claims
about cloud results.

## Contract

- Legacy execution: `GET /5100/coordinate-normalizer?x=960&y=540&width=1920&height=1080`
- New execution: `POST /5100/coordinate-normalizer` with a JSON body
- Lookup: `GET /5100/coordinate-normalizer?id=<uuid>`
- DynamoDB table: `csc5100-results`, string partition key `id`
- S3 object: `s3://csc5100-results/dylangururajan/coordinate-normalizer/<uuid>.json`
- SNS topic: `csc5100-events`
- SQS queue: `csc5100-processing`
- MQTT event topic: `csc5100/events/coordinate-normalizer`, QoS 1
- MQTT acknowledgment topic: `csc5100/status/<uuid>`, QoS 1
- HiveMQ broker: `tcp://broker.hivemq.com:1883`

Every POST generates one UUID. The exact result JSON returned by POST is passed unchanged to the
storage and messaging adapters:

```json
{
  "id": "c5f1b1b7-ccf5-4b98-80f9-f1123589a123",
  "operation": "CoordinateNormalizerLambda",
  "x": 960.0,
  "y": 540.0,
  "width": 1920.0,
  "height": 1080.0,
  "result": {
    "normalizedX": 0.5,
    "normalizedY": 0.5
  }
}
```

The deployed Lambda reads the following resource identifiers from its environment. These are names
and URLs/ARNs, not application secrets:

```text
A04_DYNAMODB_TABLE=csc5100-results
A04_S3_BUCKET=csc5100-results
A04_SNS_TOPIC_ARN=<shared csc5100-events topic ARN>
A04_SQS_QUEUE_URL=<shared csc5100-processing queue URL>
```

Its execution role needs `dynamodb:PutItem`, `dynamodb:GetItem`, `s3:PutObject`, `sns:Publish`, and
`sqs:SendMessage` for those resources.

## Local verification

Run the regular test suite and package the shaded JAR:

```bash
mvn clean package
```

Local result on 2026-10-26: **BUILD SUCCESS**, 76 tests run, 0 failures, 0 errors, and
0 skipped. The 11 coordinate-normalizer unit tests are included in that total.

The tagged public-broker integration test is intentionally separate from regular unit tests. It
uses a random UUID, subscribes to that UUID's ACK topic, publishes only synthetic coordinate data,
and waits at most 15 seconds:

```bash
mvn -Plive-mqtt verify
```

Run the desktop console from the shaded JAR and stop it with Ctrl+C:

```bash
java -cp target/csc5100-1.0.jar \
  edu.calpoly.lambdalab.dylangururajan.CoordinateNormalizerMqttSubscriber
```

## Deployed integration evidence

Set the endpoint in a shell variable, execute a POST, and copy its generated UUID. Do not put AWS
credentials or IoT certificates in the PR.

The live HiveMQ test and AWS checks below still need to be run after deployment; their results are
not claimed by the local Maven result above.

```bash
API_URL=https://api.javiergs.phd/5100/coordinate-normalizer

curl -i -X POST "$API_URL" \
  -H 'Content-Type: application/json' \
  -d '{"x":960,"y":540,"width":1920,"height":1080}'

RESULT_ID=<uuid-from-post>
curl -i "$API_URL?id=$RESULT_ID"
```

Collect screenshots or redacted CLI output showing the same `RESULT_ID` in:

1. the POST response and GET response;
2. DynamoDB item `csc5100-results` with partition key `id=$RESULT_ID`;
3. S3 object `dylangururajan/coordinate-normalizer/$RESULT_ID.json` with identical JSON;
4. an independent SNS delivery or subscription record from `csc5100-events`;
5. the `csc5100-processing` consumer logs for both AWS IoT Core and HiveMQ forwarding;
6. the Java console event display; and
7. the MQTT ACK on `csc5100/status/$RESULT_ID`.

Useful read-only AWS CLI checks, when course credentials and the AWS region are already configured:

```bash
aws dynamodb get-item \
  --table-name csc5100-results \
  --key "{\"id\":{\"S\":\"$RESULT_ID\"}}" \
  --consistent-read

aws s3 cp \
  "s3://csc5100-results/dylangururajan/coordinate-normalizer/$RESULT_ID.json" -
```

## PR evidence checklist

- [ ] `mvn clean package` summary
- [ ] `mvn -Plive-mqtt verify` summary (or note a bounded public-broker timeout and rerun evidence)
- [ ] legacy GET response
- [ ] POST response and generated UUID
- [ ] GET-by-UUID response
- [ ] DynamoDB record and S3 object with the same UUID and JSON
- [ ] independent SNS evidence
- [ ] SQS consumer, AWS IoT Core, and HiveMQ evidence
- [ ] desktop event display and ACK evidence
- [ ] exact `A04 Story: #<story-number>` line
- [ ] all seven native sub-issues closed before requesting review
