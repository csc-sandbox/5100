package edu.calpoly.lambdalab.daome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record MovingAverageData(@JsonProperty(value = "id", required = true) String id,
                                @JsonProperty(value = "samples", required = true) List<Integer> samples,
                                @JsonProperty(value = "window", required = true) Integer windowSize,
                                @JsonProperty(value = "movingAverage", required = true) List<Double> averages) {

}