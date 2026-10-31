package edu.calpoly.lambdalab.daome.data;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovingAverageData(@JsonProperty(value = "id", required = true) UUID id,
                                @JsonProperty(value = "samples", required = true) List<Integer> samples,
                                @JsonProperty(value = "window", required = true) Integer windowSize,
                                @JsonProperty(value = "movingAverage", required = true) List<Double> averages) {

}