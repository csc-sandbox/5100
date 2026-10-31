package edu.calpoly.lambdalab.daome.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovingAverageData(@JsonProperty(value = "id", required = true) String id,
                                @JsonProperty(value = "samples", required = true) List<Integer> samples,
                                @JsonProperty(value = "window", required = true) Integer windowSize,
                                @JsonProperty(value = "movingAverage", required = true) List<Double> averages) {

}