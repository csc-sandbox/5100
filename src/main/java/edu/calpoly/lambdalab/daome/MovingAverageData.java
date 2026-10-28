package edu.calpoly.lambdalab.daome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import software.amazon.awssdk.annotations.NotNull;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record MovingAverageData(@JsonProperty(value = "id", required = true) String id,
                                @JsonProperty(value = "samples", required = true) List<Integer> samples,
                                @JsonProperty(value = "window", required = true) Integer windowSize,
                                @JsonProperty(value = "movingAverages", required = true) List<Double> averages) {

    public Map<String, AttributeValue> toAttributeValue() {
        Map<String, AttributeValue> output = new HashMap<>();

        output.put("id", AttributeValue.builder().s(id).build());
        output.put("samples",
                AttributeValue.builder()
                              .l(samples.stream()
                                        .map((Integer i) -> AttributeValue.builder().n(Integer.toString(i)).build())
                                        .toList())
                              .build());
        output.put("window", AttributeValue.builder().n(Integer.toString(windowSize)).build());
        output.put("movingAverage",
                AttributeValue.builder()
                              .l(averages.stream()
                                         .map((Double i) -> AttributeValue.builder().n(Double.toString(i)).build())
                                         .toList())
                              .build());
        return output;
    }
}