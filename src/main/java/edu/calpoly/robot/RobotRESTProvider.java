package edu.calpoly.robot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates a clie(nt to send REST requests to a specific robot on the robot resource.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0.0 (9/29/2026)
 */
public class RobotRESTProvider {
    
    private final HttpClient client;
    private final int port;
    private final int robotId;

    /**
     * Create the client and sets the port and resouce to make requests to.
     * @param port the port to connect to on localhost.
     * @param robotId the specific robot resource to send requests to.
     */
    RobotRESTProvider(int port, int robotId){
        this.port = port;
        this.robotId = robotId;
        client = HttpClient.newHttpClient();
    }

    /**
     * Allows the PUT of a RobotMessage object.
     * @param body
     * @throws JsonProcessingException
     * @throws Exception
     */
    public void requestPUT(RobotMessage body) throws JsonProcessingException, Exception {
        ObjectMapper mapper = new ObjectMapper();
        requestPUT(mapper.writeValueAsString(body));
    }
    /**
     * Creates a PUT request and sends it.
     * @param body the message for the request.
     * @return the HttpResponse from the request.
     * @throws IOException
     * @throws InterruptedException
     */
    HttpResponse<String> requestPUT(String body) throws IOException, InterruptedException{
        HttpRequest PUTRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/robot"))
            .PUT(BodyPublishers.ofString(body))
            .build();

        return client.send(PUTRequest, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Creates a GET request and sends it.
     * @return the HttpResponse from the request.
     * @throws IOException
     * @throws InterruptedException
     */
    HttpResponse<String> requestGET() throws IOException, InterruptedException{
        HttpRequest GETRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/robot"))
            .GET()
            .build();

        return client.send(GETRequest, HttpResponse.BodyHandlers.ofString());
    }
}
