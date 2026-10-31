package edu.calpoly.lambdalab.daome.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record MovingAverageLambdaEvent(@JsonProperty(value = "id") UUID id,
                                       @JsonProperty(value = "operation") String operation,
                                       @JsonProperty(value = "result") MovingAverageData result) {

}
