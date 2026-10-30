package edu.calpoly.robot;

import java.io.IOException;

import javax.swing.JFrame;

/**
 * RobotServer creates the robotGUI, robotBlackBoard, and sets up adapters for REST and MQTT.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0.1 (9/26/2026)
 */
public class RobotServer {
    protected static final String MQTT_HOST = "broker.hivemq.com";
    protected static final int MQTT_PORT = 1883;

    protected static final int REST_PORT = 5001;
    protected static final int ROBOT_ID = 1;

    /**
     * The entry point for the server.
     * @param args No argument functionalities. 
     */
    public static void main(String[] args) {
        // Frame to put the robot in.
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        // Robot GUI + configuration.
        RobotGUI robotGUI = new RobotGUI();
        robotGUI.showData(true);
        robotGUI.showRobot(true, 1000, 1000);
        // Add Robot to Frame.
        robotGUI.addToFrame(frame);
        // Show All visuals.
        frame.setVisible(true);
        
        // RobotBlackBoard to Manage Data.
        RobotBlackBoard rbb = new RobotBlackBoard();
        rbb.callbackOnPost(robotGUI::update);

        // MQTT adapter.
        Thread t1 = new RobotMQTTAdapter(MQTT_HOST, MQTT_PORT, ROBOT_ID, rbb); //subscriber, grabs callbacks from rbb
        t1.start();

        // REST adapter.
        Thread t2;
        try {
            t2 = new RobotRESTAdapter(REST_PORT, ROBOT_ID, rbb); // GET and PUT, grabs callbacks from rbb.
            t2.start();
        } catch (IOException e) {
            System.out.println("Could not start RobotRESTAdapter server.");
        }
    }
}
