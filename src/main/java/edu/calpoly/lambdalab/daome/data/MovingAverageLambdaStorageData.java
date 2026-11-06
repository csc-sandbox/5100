package edu.calpoly.lambdalab.daome.data;

import com.fasterxml.jackson.databind.JsonNode;

public record MovingAverageLambdaStorageData(String owner, String operation, JsonNode responseJson) {
}
