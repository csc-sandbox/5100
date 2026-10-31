package edu.calpoly.lambdalab.daome.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovingAverageLambdaEvent(@JsonProperty(value = "id") String id,
                                       @JsonProperty(value = "operation") String operation,
                                       @JsonProperty(value = "result") MovingAverageData result) {

}
