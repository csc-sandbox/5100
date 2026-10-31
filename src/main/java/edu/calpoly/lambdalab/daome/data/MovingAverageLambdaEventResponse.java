package edu.calpoly.lambdalab.daome.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MovingAverageLambdaEventResponse(@JsonProperty(value = "id") String id,
                                               @JsonProperty(value = "operation") String operation,
                                               @JsonProperty(value = "status") EventStatus status,
                                               @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty(value =
                                                       "errorMessage") String message) {}
