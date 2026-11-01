# A04 — Cloud-to-Desktop Integration

## **Class:** `RobotDistanceLambda`

**Package:** `edu.calpoly.lambdalab.paulmotter`  
**Public endpoint:** `https://api.javiergs.phd/5100/robot-distance`

**HTTP method:** `GET`  
**Route:** `/5100/robot-distance?x1=&y1=&z1=&x2=&y2=&z2= `  
**Input:** Query: x1,y1,z1,x2,y2,z2.\
**Return:** Calculated distance.
**Functionality:** Maintains the legacy functionality of two input points and one output distance.\

**HTTP method:** `GET`  
**Route:** `/5100/robot-distance?id={UUID}`  
**Input:** Query: id\
**Return:** The dynamoDB entry JSON with the given id.\
**Functionality:** Only retrieves rows if the operation and author fields match those stated in `RobotDistanceLambda`\

**HTTP method:** `POST`  
**Route:** `/5100/robot-distance`  
**Input:** Body: {x1,y1,z1,x2,y2,z2}\
**Return:** The dynamoDB entry with the generated id.\
**Functionality:** Creates an full entry a DynamoDB table and S3 Bucket. A shorter summary is published to SNS and queued to SQS.

**Testing:** `RobotDistanceLambdaTest`\
**Package:** `test.java.lambdalab.paulmotter`\
**Tests:** Contains suites of tests for the legacy GET, GET, and POST functionality. Uses mock implementations of the `RobotDistanceLambda.MessagingAdapter` and `RobotDistanceLambda.StorageAdapter` interfaces.

**Run:**

```bash
mvn test -Dtest=RobotDistanceLambdaTest
```

---

## **Class:** `RobotDistanceDesktopClient`

**Package:** `edu.calpoly.lambdalab.paulmotter`

**Functionality:** When run locally it connects to an MQTT broker (default `tcp://broker.hivemq.com:1883`), subscribes to the event topic, displays each received `RobotDistanceLambda` event, and publishes a QoS 1 acknowledgment to the status topic. Events are printed to standard out by default.\
**Subscribe topic (events):** `csc5100/events/robot-distance`\
**Publish topic (ACK/status):** `csc5100/status/<UUID>`

**Run:**

```bash
mvn clean package
java -cp target/csc5100-1.0.jar edu.calpoly.lambdalab.paulmotter.RobotDistanceDesktopClient [brokerUri]
```

**Event contract (consumed):** `{ "id": <uuid>, "operation": "RobotDistanceLambda", "result": <distance> }`.\
**ACK contract (published):** `{ "id", "status": "ACK", "operation", "result", "client", "receivedAt" }`, has the same `id` as the consumed event.

**Testing:** `RobotDistanceEventHandlerTest`\
**Package:** `test.java.lambdalab.paulmotter`\
**Tests:** Unit tests (no broker) for event parsing and ACK logic via `RobotDistanceEventHandler`. Covers valid-event parsing, the `lambda`/integer-result aliases, preservation of extra fields, rejection of malformed/invalid events (bad id, wrong operation, missing/negative/non-numeric result, null payload), single-ACK publishing to the status topic, display formatting, non-acknowledgment of rejected events, publish-failure handling, and QoS 1 ACK messages.

**Integration Testing:** `RobotDistanceDesktopClientLiveTest`\
**Package:** `test.java.lambdalab.paulmotter`\
**Tests:** Live end-to-end test against the public HiveMQ broker. Publishes a synthetic event with a unique id to the event topic and asserts that the running client displays it and publishes a matching ACK to the status topic within a bounded timeout. Tagged `integration` and skipped by default; enable with:

```bash
mvn test -Dtest=RobotDistanceDesktopClientLiveTest -Dhivemq.live=true
```
