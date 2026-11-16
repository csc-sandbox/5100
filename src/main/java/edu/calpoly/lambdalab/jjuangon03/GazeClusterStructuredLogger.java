package edu.calpoly.lambdalab.jjuangon03;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Emits one JSON object per application event for CloudWatch Logs Insights queries. */
final class GazeClusterStructuredLogger implements GazeClusterLambda.EventLogger {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BiConsumer<Context, String> sink;

    GazeClusterStructuredLogger() {
        this((context, json) -> {
            if (context != null) context.getLogger().log(json + System.lineSeparator());
        });
    }

    GazeClusterStructuredLogger(Consumer<String> sink) {
        this((context, json) -> sink.accept(json));
    }

    private GazeClusterStructuredLogger(BiConsumer<Context, String> sink) { this.sink = sink; }

    @Override
    public void log(Context context, String event, Map<String, Object> fields) {
        try {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("event", event);
            json.put("operation", GazeClusterLambda.OPERATION);
            json.putAll(fields);
            sink.accept(context, mapper.writeValueAsString(json));
        } catch (Exception exception) {
            // Logging must never turn a valid Lambda operation into a failure.
        }
    }
}
