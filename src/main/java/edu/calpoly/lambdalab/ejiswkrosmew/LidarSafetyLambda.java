package edu.calpoly.lambdalab.ejiswkrosmew;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * AWS Lambda function that checks the safety of Lidar distances read
 * Gives a "warning" status if any distances read is too close (relative to a threshold)
 * Heavily based on edu.calpoly.lambdalab.instructor.TemperatureLambda
 *
 * @author James Yaguma
 * @version 1.1 (2026-10-27)
 */
public class LidarSafetyLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    interface Storage {
        void save(String id, String json);

        String get(String id);
    }

    interface Messaging {
        void publish(String json);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Storage storage;
    private final Messaging messaging;

    /**
     * Default constructor
     * Try to use env variables by default
     */
    LidarSafetyLambda() {
        this(
            System.getenv("A04_DYNAMODB_TABLE"),
            System.getenv("A04_S3_BUCKET"),
            System.getenv("A04_SNS_TOPIC_ARN"),
            System.getenv("A04_SQS_QUEUE_URL")
        );
    }

    /**
     * Create instances of Storage and Messaging from the parameters
     *
     * @param table    Name of the DynamoDB table to access
     * @param bucket   Name of the S3 bucket to access
     * @param topicArn Name of the topic that for SNS
     * @param queueUrl Name of the queue url for SQS
     */
    LidarSafetyLambda(String table, String bucket, String topicArn, String queueUrl) {
        this(table != null && !table.isBlank() && bucket != null && !bucket.isBlank()
                ? new LidarSafetyStorage(table, bucket) : null,
            topicArn != null && !topicArn.isBlank() && queueUrl != null && !queueUrl.isBlank()
                ? new LidarSafetyMessaging(topicArn, queueUrl) : null);
    }

    /**
     * Use provided storage
     *
     * @param storage Storage to use instead of defaulting to DynamoDB/S3 storage
     */
    LidarSafetyLambda(Storage storage) {
        this(storage, null);
    }

    /**
     * Use both provided storage and messaging
     *
     * @param storage   Storage to use instead of defaulting to DynamoDB/S3 storage
     * @param messaging Messaging to use instead of SNS and SQS
     */
    LidarSafetyLambda(Storage storage, Messaging messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    /**
     * Handle a request
     *
     * @param input   The Lambda Function input
     * @param context The Lambda execution environment context object.
     * @return The HTTP response as a Map<String, Object>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        try {
            // Make sure input is actually something
            if (input == null) {
                return errorResponse(400, "Invalid Request");
            }

            // Try to grab query parameters if they exist
            Object tempQuery = input.get("queryStringParameters");
            Map<String, Object> query = Map.of();
            if (tempQuery instanceof Map) {
                query = (Map<String, Object>) tempQuery;
            }

            // Check the HTTP request method
            String method = getReqMethod(input, !query.isEmpty());

            // If it's a GET request by UUID, try to do it
            if (method.equals("GET") && query.containsKey("id")) {
                String id = String.valueOf(query.get("id"));
                if (!isUuid(id)) { // ID is invalid
                    return errorResponse(400, "Invalid id");
                }
                if (storage == null) { // Storage doesn't exist
                    return errorResponse(500, "Storage not configured");
                }
                String json;
                try {
                    json = storage.get(id);
                } catch (Exception e) {
                    return errorResponse(500, "Unable to persist result into storage");
                }
                if (json == null) { // Not found
                    return errorResponse(404, "Not found");
                }

                // Success
                return httpResponse(200, json);
            }

            // Check for legacy get
            boolean legacyGet = method.equals("GET") || (input.get("httpMethod") == null && !query.isEmpty());

            // Use either query or post body depending on legacy get
            Map<String, Object> body;
            if (!legacyGet) {
                body = mapper.readValue(input.get("body").toString(), Map.class);
            } else {
                body = new HashMap<>(query);

                // Need to manually parse threshold since it's a string
                body.put("threshold", Double.parseDouble(body.get("threshold").toString()));

                // Have to manually parse the distances since it's a string not a list
                String distString = body.get("distances").toString();
                distString = distString.substring(1, distString.length() - 1);
                List<Double> parsedDists = new ArrayList<>();
                for (String dist : distString.split(",")) {
                    parsedDists.add(Double.parseDouble(dist));
                }

                body.put("distances", parsedDists);
            }

            // Convert body to distances and thresholds
            List<Double> distances = new ArrayList<>();
            for (Object dist : (List<Object>) body.get("distances")) {
                distances.add(((Number) dist).doubleValue());
            }

            double threshold = (Double) body.get("threshold");

            // Get the response result as a Map
            Map<String, Object> response = new HashMap<>(safetyCheck(distances, threshold));

            // If legacy get, we're not persisting results so end here
            if (legacyGet) {
                return httpResponse(200, mapper.writeValueAsString(response));
            }

            // Create/Reuse a UUID for the successful query and add it to the response
            String id = body.getOrDefault("id", UUID.randomUUID()).toString();
            response.put("id", id);

            // Update storage if possible
            if (storage != null) {
                try {
                    storage.save(id, mapper.writeValueAsString(response));
                    response.put("storage", Map.of(
                        "dynamodb", "stored",
                        "s3", "stored"
                    ));
                } catch (Exception e) {
                    response.put("storage", Map.of(
                        "dynamodb", "failed",
                        "s3", "failed"
                    ));
                }
            } else {
                response.put("storage", Map.of(
                    "dynamodb", "not_configured",
                    "s3", "not_configured"
                ));
            }

            // Send messages if possible
            if (messaging != null) {
                try {
                    messaging.publish(mapper.writeValueAsString(response));
                    response.put("messaging", Map.of(
                        "sns", "published",
                        "sqs", "queued"
                    ));
                } catch (Exception e) {
                    response.put("messaging", Map.of(
                        "sns", "failed",
                        "sqs", "failed"
                    ));
                }
            } else {
                response.put("messaging", Map.of(
                    "sns", "not_configured",
                    "sqs", "not_configured"
                ));
            }

            // Return the response as a JSON string
            return httpResponse(200, mapper.writeValueAsString(response));
        } catch (Exception e) {
//            System.err.println("Invalid Request Received due to: " + e.getMessage());
            return errorResponse(400, "Invalid request");
        }
    }

    /**
     * Perform a safety check to ensure no distance measured was too close
     * Given threshold determines what is too close
     *
     * @param distances A list of distances measured to safety check
     * @param threshold The threshold that notes what distance becomes too close
     * @return A map of String to Object containing the safety status and minimum distance recorded
     */
    private Map<String, Object> safetyCheck(List<Double> distances, double threshold) {
        double minDist = Double.MAX_VALUE;
        for (double dist : distances) {
            if (dist < 0) {
                throw new IllegalArgumentException("Invalid distance value: A distance of " + dist + " was found which is not greater than 0.");
            }
            if (minDist > dist) {
                minDist = dist;
            }
        }

        String status = "SAFE";
        if (minDist < threshold) {
            status = "WARNING";
        }
        return Map.of(
            "status", status,
            "minDist", minDist
        );
    }

    /**
     * Generate an HTTP response in the form of a Map<String, Object>
     *
     * @param statusCode The status code to use
     * @param body       The body of the HTTP response
     * @return A Map<String, Object> representation of an HTTP response
     */
    private Map<String, Object> httpResponse(int statusCode, String body) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", body
        );
    }

    /**
     * Helper function to streamline error HTTP responses
     * Calls {@link #httpResponse(int, String)} but only needs an error message string instead of a whole JSON body
     *
     * @param statusCode The status code to use
     * @param errorTxt   The error message that goes in the HTTP response bod
     * @return A Map<String, Object> representation of an HTTP response
     */
    private Map<String, Object> errorResponse(int statusCode, String errorTxt) {
        return httpResponse(statusCode, "{\"error\":\"" + errorTxt + "\"}");
    }

    /**
     * Helper function to assist with figuring out if a request is a GET or POST
     *
     * @param input       Same input variable that was passed in {@link #handleRequest(Map, Context)}
     * @param queryExists Boolean that should only be true iff query parameters were found
     * @return Either "GET" or "POST" depending on what was determined to be the method call
     */
    @SuppressWarnings("unchecked")
    private String getReqMethod(Map<String, Object> input, boolean queryExists) {
        // If httpMethod exists, use it
        Object httpMethod = input.get("httpMethod");
        if (httpMethod != null) {
            return (String) httpMethod;
        }
        // Otherwise, try to find it in the request context

        // If we can't figure it out we'll guess based on if a query exists or not
        String defaultMethod = queryExists ? "GET" : "POST";

        // Try to get request context
        Object requestContext = input.get("requestContext");
        if (!(requestContext instanceof Map)) {
            return defaultMethod;
        }

        // If that worked, try to get the http field
        Object http = ((Map<String, Object>) requestContext).get("http");
        if (!(http instanceof Map)) {
            return defaultMethod;
        }

        // If that work, try to get the method value, or go to default
        return String.valueOf(((Map<String, Object>) http).getOrDefault("method", defaultMethod)).toUpperCase();
    }

    /**
     * Helper function to check if string is a valid UUID
     * Almost directly copied from edu.calpoly.lambdalab.instructor.TemperatureLambda
     *
     * @param id The UUID to test
     * @return True if the UUID is a valid UUID. Otherwise, returns false
     */
    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (Exception e) {
            return false;
        }
    }
}
