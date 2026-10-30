package edu.calpoly.robot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
/**
 * RobotRESTAdapter is an adapter class for the passed in RobotBlackBoard that
 * updates and retrieves the blackboard data everytime a REST request is given.
 * 
 * @author Paul Motter (PaulMotter)
 * @author Jess A (0x10jalencas)
 * @version 1.0.1 (9/28/2026)
 */
public class RobotRESTAdapter extends Thread{
    /* expected JSON format
        {
            "jointAngles": [v1, v2, v3, v4, v5, v6],
            "postiions": [x, y, z]    
        }
    */
    private ObjectMapper om;
    private HttpServer server;
    private int robotID;
    private Consumer<RobotMessage> onPUT;
    private Supplier<RobotMessage> onGET;

    /**
     * Creates a server that can run on a thread.
     * Listens for requests on the resource "/robot".
     * @param RESTport The port on which the server runs.
     * @param robotID The resource object id.
     * @throws IOException from creating the server.
     */
    public RobotRESTAdapter(int RESTPort, int robotID, RobotBlackBoard rbb) throws IOException{
        this.robotID = robotID;
        this.onPUT = rbb::post;
        this.onGET = rbb::read;
        // Create JSON parser.
        om = new ObjectMapper();

        // Create server.
        server = HttpServer.create(
            new InetSocketAddress(RESTPort), 0
        );

        // Setup Server.
        server.createContext("/robot", exchange -> {
            // Get the http method.
            String method = exchange.getRequestMethod().toUpperCase();
            // Match http method.
            switch (method) {
                case "GET":
                    robotGET(exchange);
                    break;
                case "POST":
                    reply(405, "POST does not exist.".getBytes(), exchange);
                    break;
                case "PUT":
                    robotPUT(exchange);
                    break;
                case "DELETE":
                    reply(405, "DELETE does not exist.".getBytes(), exchange);
                    break;
                default:
                    reply(405, "Method does not exist.".getBytes(), exchange);
                    break;
            }
        });
    }

    /**
     * Returns the latest robot data as JSON.
     *
     * @param exchange current HTTP exchange
     */
    private void robotGET(HttpExchange exchange) {
        RobotMessage message = onGET.get();

        if (message == null) {
            reply(
                404,
                "No robot data available.".getBytes(StandardCharsets.UTF_8),
                exchange);
            return;
        }

        try {
            exchange.getResponseHeaders().set(
                "Content-Type", "application/json");
            reply(200, om.writeValueAsBytes(message), exchange);
        } catch (IOException e) {
            reply(
                500,
                "Unable to serialize robot data.".getBytes(StandardCharsets.UTF_8),
                exchange);
        }
    }

    /**
     * Defines the PUT method for the context.
     * @param exchange
     */
    private void robotPUT(HttpExchange exchange){
        // Parse JSON
        RobotMessage rm;
        try {
            rm = om.readValue(
                exchange.getRequestBody(),
                RobotMessage.class
            );
        } catch (IOException e){
            // Invalid JSON
            reply(400, "Invalid JSON".getBytes(), exchange);
            return;
        }
        
        // Complete Action
        onPUT.accept(rm);

        // Try to Respond.
        try {
            byte[] reply = om.writeValueAsBytes(rm);
            reply(200, reply, exchange);
        } catch (IOException e){}
        return;
    }


    /**
     * Starts the server created in the constructor.
     * @see java.lang.Thread#run()
     */
    public void run(){
        server.start();
        InetSocketAddress address = server.getAddress();
        System.out.println("REST server running at " 
        + "http://" + address.getHostString() + ":" + address.getPort() + "/robot");
    }

    /**
     * Returns the port the server is bound to. Useful when the server
     * was created with port 0 (any free port).
     * @return the bound port.
     */
    int getPort(){
        return server.getAddress().getPort();
    }

    /**
     * Stops the server immediately.
     */
    void shutdown(){
        server.stop(0);
    }

    /**
     * A response handler for the exchange. Define the statusCode, body, and the exchange to send on.
     * Returns whether there was an error sending the reply.
     * @param statusCode
     * @param body
     * @param exchange
     * @return
     */
    private static boolean reply(int statusCode, byte[] body, HttpExchange exchange){
        try {
            // Attempt to send.
            exchange.sendResponseHeaders(
                statusCode, body.length
            );
        
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();

            exchange.close();

            return true;
        } catch (IOException | IllegalArgumentException e){
            //failed to send.
            return false;
        }
    }

    
}

