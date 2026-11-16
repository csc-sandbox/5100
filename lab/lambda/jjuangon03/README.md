# Lambda Lab — GazeClusterLambda

**Package:** `edu.calpoly.lambdalab.jjuangon03`  
**Class:** `GazeClusterLambda`  
**HTTP method:** `POST`  
**Lookup method:** `GET`  
**Route:** `/5100/gaze-clusters`  
**Public endpoint:** `https://api.javiergs.phd/5100/gaze-clusters`

## Problem

Receive normalized gaze points `(x,y)` and identify gaze zones using DBSCAN clustering.

**The goal of this lab is AWS Lambda integration, testing, CI/CD, and deployment — not implementing DBSCAN.** The clustering algorithm below is provided. You may use it as-is.

## Contract

Request body:

```json
{
  "points": [
    {"x": 0.10, "y": 0.20},
    {"x": 0.12, "y": 0.21},
    {"x": 0.11, "y": 0.18},
    {"x": 0.75, "y": 0.70},
    {"x": 0.78, "y": 0.72},
    {"x": 0.76, "y": 0.69}
  ],
  "epsilon": 0.08,
  "minPoints": 2
}
```

Response:

```json
{
  "id": "generated-uuid",
  "operation": "GazeClusterLambda",
  "result": {
    "numberOfClusters": 2,
    "clusters": [
      {"id": 0, "centerX": 0.11, "centerY": 0.20, "points": 3},
      {"id": 1, "centerX": 0.76, "centerY": 0.70, "points": 3}
    ]
  },
  "numberOfClusters": 2,
  "clusters": [
    {"id": 0, "centerX": 0.11, "centerY": 0.20, "points": 3},
    {"id": 1, "centerX": 0.76, "centerY": 0.70, "points": 3}
  ]
}
```

Return HTTP `200` for a valid request and HTTP `400` for invalid input.
The original `numberOfClusters` and `clusters` fields are preserved. Each successful
POST now also returns a correlation `id` and `operation` for A04 integrations.

Retrieve a result with `GET /5100/gaze-clusters?id=<uuid>`. The Lambda also accepts
the UUID as a path parameter when API Gateway is configured with a matching route.
GET returns the stored result JSON, `404` for an unknown UUID, and `400` for an
invalid UUID.

## A04 Cloud and Desktop Integration

Configure these Lambda environment variables in the deployment environment; do not
commit AWS credentials or secrets:

- `A04_DYNAMODB_TABLE` — `csc5100-results`
- `A04_S3_BUCKET` — `csc5100-results`
- `A04_SNS_TOPIC_ARN` — SNS topic ARN for `csc5100-events`
- `A04_SQS_QUEUE_URL` — queue URL for `csc5100-processing`

The same JSON result (including its UUID) is persisted to DynamoDB and S3. SNS and
SQS publishing are independent; configure each destination separately. The SQS
event includes `operation: "GazeClusterLambda"` for function-specific MQTT routing.
S3 objects use the `jjuangon03/gaze-clusters/<uuid>.json` key.

Run the console subscriber with Maven's compiled classpath or from the IDE:

`edu.calpoly.lambdalab.jjuangon03.GazeClusterMqttSubscriber`

It listens at QoS 1 on `csc5100/events/gaze-clusters` at
`broker.hivemq.com:1883`, displays validated JSON events, and publishes a QoS 1
receipt to `csc5100/status/<uuid>`. Use synthetic data only on the public broker.
The live HiveMQ JUnit test is tagged `integration` and is opt-in:
`mvn -Dhivemq.integration=true -Dgroups=integration test`.

## Provided DBSCAN Algorithm

Use this algorithm as the business-logic component. You do **not** need to design or reimplement DBSCAN.

```text
DBSCAN(points, epsilon, minPoints)

    clusterId = 0
    labels = all UNVISITED

    for each point P in points

        if P is already visited
            continue

        mark P as visited

        neighbors = all points whose
                    Euclidean distance from P <= epsilon

        if number of neighbors < minPoints
            mark P as NOISE
            continue

        create cluster clusterId
        add P to cluster

        queue = neighbors

        while queue is not empty

            Q = remove next point from queue

            if Q has not been visited

                mark Q as visited

                qNeighbors = all points whose
                             Euclidean distance from Q <= epsilon

                if number of qNeighbors >= minPoints
                    add qNeighbors to queue

            if Q is not already assigned to a cluster
                assign Q to cluster clusterId

        clusterId = clusterId + 1

    return clusters
```

Distance between two gaze points:

```text
distance(P, Q) =
    sqrt((P.x - Q.x)^2 + (P.y - Q.y)^2)
```

For each resulting cluster, calculate:

```text
centerX = average x of all points in cluster
centerY = average y of all points in cluster
points  = number of points in cluster
```

Noise points are not returned as clusters.

## Your Work

Create:

- `src/main/java/edu/calpoly/lambdalab/jjuangon03/GazeClusterLambda.java`
- `src/test/java/edu/calpoly/lambdalab/jjuangon03/GazeClusterLambdaTest.java`

Focus on the Lambda layer:

1. Read the API Gateway POST body.
2. Deserialize the JSON request.
3. Validate `points`, `epsilon`, and `minPoints`.
4. Call the provided DBSCAN business logic.
5. Convert the clusters into the required JSON response.
6. Return the appropriate HTTP status.
7. Keep the clustering logic separate from `handleRequest()`.

Use the instructor `TemperatureLambda` as the reference for API Gateway request/response handling.

Write at least three meaningful JUnit tests:

- two obvious gaze clusters,
- one gaze cluster,
- invalid or insufficient input.

## Test the Deployed Lambda

After your code passes GitHub Actions and is deployed, run this from the IntelliJ Terminal:

`curl -X POST "https://api.javiergs.phd/5100/gaze-clusters" -H "Content-Type: application/json" -d '{"points":[{"x":0.10,"y":0.20},{"x":0.12,"y":0.21},{"x":0.11,"y":0.18},{"x":0.75,"y":0.70},{"x":0.78,"y":0.72},{"x":0.76,"y":0.69}],"epsilon":0.08,"minPoints":2}'`

Your response must be JSON and follow the contract above.

## Pull Request

Create **one PR** for A04 after creating and closing the seven required native
sub-issues under the A04 Story.

PR title:

`A04 - GazeClusterLambda`

Include `A04 Story: #<issue>` in the PR body. Include:

- Lambda: `GazeClusterLambda`
- The original POST contract and GET-by-UUID curl requests and responses
- JUnit and `mvn clean package` results
- DynamoDB and S3 persistence evidence using the same UUID and JSON
- Independent SNS and SQS publishing evidence, including both broker outputs
- Desktop MQTT receipt and UUID-specific ACK evidence
- The seven native sub-issues closed before review
