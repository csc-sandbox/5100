package edu.calpoly.lambdalab.paulmotter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AWS Lambda function that calculates the distance between two 3 dimensional points.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0 
 */
public class RobotDistanceLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public static final String OPERATION_NAME = "RobotDistance";
    public static final String OWNER_NAME = "paulmotter";

    interface StorageAdapter{
        public void save(RobotDistanceLambdaData data, String json);
        public String get(String id);
    }
    interface MessagingAdapter{
        public void publish(String json);
    }

    public class RobotDistancedata{

    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final StorageAdapter storage;
    private final MessagingAdapter messaging;
    private RobotDistanceLambdaData data;

    /**
     * Default constructor used in prod with provided credentials.
     */
    public RobotDistanceLambda(){
        this(
            System.getenv("A04_DYNAMODB_TABLE"), 
            System.getenv("A04_S3_BUCKET"),
            System.getenv("A04_SNS_TOPIC_ARN"), 
            System.getenv("A04_SQS_QUEUE_URL")
        );
    }

    /**
     * Custom constructor used to bring your own functionality.
     * @param SA StorageAdapter 
     * @param MA MessagingAdapter
     */
    public RobotDistanceLambda(StorageAdapter SA, MessagingAdapter MA){
        storage = SA;
        messaging = MA;
    }

    private RobotDistanceLambda(String dbTable, String s3Bucket, String snsTopicArn, String sqsUrl) {
        this(
            notBlank(dbTable) && notBlank(s3Bucket)
                ? new RobotDistanceStorageAdapter(dbTable, s3Bucket) : null,
            notBlank(snsTopicArn) && notBlank(sqsUrl)
                ? new RobotDistanceMessagingAdapter(snsTopicArn, sqsUrl) : null);
    }
    private static boolean notBlank(String s){
        return s != null && !s.isBlank();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            if (event == null) {
                return httpResponse(400, "{\"error\":\"Invalid request\"}");
            }
            // if requestContext exists use it, otherwise empty map.
            Map<String, Object> requestContext = event.get("requestContext") instanceof Map ? (Map<String, Object>) event.get("requestContext") : Map.of();
            // if http exists within request context use it, otherwise empty map.
            Map<String, Object> http = requestContext.get("http") instanceof Map ? (Map<String, Object>) requestContext.get("http") : Map.of();
            // if httpMethod exists within http then use it 
            String method = String.valueOf(event.getOrDefault("httpMethod", http.getOrDefault("method", event.get("queryStringParameters") != null ? "GET" : "POST"))).toUpperCase();
            // Get the query parameter map.
            Map<String, Object> query = event.get("queryStringParameters") instanceof Map ? (Map<String, Object>) event.get("queryStringParameters") : Map.of();
            // Get the body map. API Gateway sends the body as a JSON string; tests may pass a Map.
            Map<String, Object> body = parseBody(event.get("body"));
            // Match to the proper method.
            if ("GET".equals(method) && query.containsKey("id")) {
                return processGET(query);
            }
            else if("GET".equals(method) && !query.containsKey("id")){
                return processLegacyGET(query);
            }
            else if("POST".equals(method)){
                return processPOST(body);
            }
            else{
                return httpResponse(405, "{\"error\":\"Unsupported Method\"}");
            }

        }catch (IllegalArgumentException e) {
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        } catch (Exception e) {
        if (context != null) context.getLogger().log("RobotDistanceLambda failure: " + e.getClass().getSimpleName());
            return httpResponse(500, "{\"error\":\"Operation failed\"}");
        }
    }

    private Map<String, Object> processGET(Map<String, Object> query){
        String UUID = String.valueOf(query.get("id"));
        // Input validation.
        if (!isUuid(UUID)) return httpResponse(400, "{\"error\":\"Invlaid id\"}");

        if (storage == null) return httpResponse(503, "{\"error\":\"Storage not configured\"}");
        String result = storage.get(UUID);
        // Result exists check.
        if (result == null) return httpResponse(404, "{\"error\":\"Not found\"}");
        else return httpResponse(200, result);
    }

    /**
     * Used to keep original functionality for legacy code. 
     * Calculates the distance between two given points.
     * @param query 
     * @return Map Response.
     */
    private Map<String, Object> processLegacyGET(Map<String, Object> query)
    {
        // float[] pos1 = new float[3];
        // float[] pos2 = new float[3];
       try{
            // Parsing can throw errors.
            data = RobotDistanceLambdaData.fromParams(query);
            if (query == null) throw new IllegalArgumentException();
            // pos1[0] = Float.parseFloat((String) query.get("x1"));
            // pos1[1] = Float.parseFloat((String) query.get("y1"));
            // pos1[2] = Float.parseFloat((String) query.get("z1"));
            // pos2[0] = Float.parseFloat((String) query.get("x2"));
            // pos2[1] = Float.parseFloat((String) query.get("y2"));
            // pos2[2] = Float.parseFloat((String) query.get("z2"));

            // calculateDistance checks to NaN and Infinity.
            data.distance = calculateDistance(data);

            Map<String, Object> output = new HashMap<>();
            output.put("distance", data.distance);
                // mapper can throw error. 
            return httpResponse(200, mapper.writeValueAsString(output));
        } catch (Exception e){
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        } 
    }

    private Map<String, Object> processPOST(Map<String, Object> params) throws JsonProcessingException{
        try {
            // Parse, calculate distance, save.
            data = RobotDistanceLambdaData.fromParams(params);
            data.distance = calculateDistance(data);
            data.id = UUID.randomUUID().toString();
            String jsonData = mapper.writeValueAsString(data);
            if (storage == null) return httpResponse(503, "{\"error\":\"Storage not configured\"}");
            storage.save(data, jsonData);
            // Parse event message and publish.
            Map<String, Object> eventMap = new HashMap<>();
            eventMap.put("id", data.id);
            eventMap.put("operation", "RobotDistanceLambda");
            eventMap.put("result", data.distance);
            String jsonEvent = mapper.writeValueAsString(eventMap);
            if (messaging == null) return httpResponse(503, "{\"error\":\"Messaging not configured\"}");
            messaging.publish(jsonEvent);
            // Return
            return httpResponse(200, jsonData);
        } catch (IllegalArgumentException e){
            return httpResponse(400, "{\"error\":\"Invalid request\"}");
        }
    }

    /**
     * Converts the event body into a map.
     * Accepts a Map (direct invocation/tests) or a JSON object string (API Gateway).
     * @param rawBody the "body" value from the event.
     * @return the body as a map, or an empty map if there is no body.
     * @throws IllegalArgumentException if the body is not a JSON object.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(Object rawBody) {
        if (rawBody instanceof Map) return (Map<String, Object>) rawBody;
        if (rawBody instanceof String s && !s.isBlank()) {
            try {
                Map<String, Object> parsed = mapper.readValue(s, Map.class);
                return parsed == null ? Map.of() : parsed;
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Body must be a JSON object", e);
            }
        }
        return Map.of();
    }

    private Map<String, Object> httpResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
            response.put("statusCode", statusCode);
            response.put("headers", Map.of("Content-Type", "application/json"));
            response.put("body", body);
        return response;
    }

    /**
     * Tests if the parameter is a valid UUID.
     * @param id
     * @return
     */
    private boolean isUuid(String id) {
        try { return UUID.fromString(id).toString().equals(id); }
        catch (Exception e) { return false; }
    }

    /**
     * Calculates the distance between two points.
     * Makes sure the inputs are 3 dimensional positions.
     * Makes sure the output is a real value.
     */
    private float calculateDistance(RobotDistanceLambdaData data){
        // if(pos1.length != 3 || pos2.length != 3){
            // throw new IllegalArgumentException("pos1 and pos2 must both have 3 elements.");
        // }
        // float[] difference = {pos2[0]-pos1[0], pos2[1]-pos1[1], pos2[2]-pos1[2]};
        float[] difference = {data.x2-data.x1, data.y2-data.y1, data.z2-data.z1};
        float distance = (float) Math.sqrt(difference[0]*difference[0] + difference[1]*difference[1] + difference[2]*difference[2]);
        if (!Float.isFinite(distance)){
            throw new IllegalArgumentException("distance is infinite or NaN.");
        }
        return distance;
    }
    
}
