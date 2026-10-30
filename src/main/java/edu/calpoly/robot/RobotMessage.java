package edu.calpoly.robot;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RobotMessage is a data class for parsing ROBOT messages and creating copies of the message.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0.1 (9/26/2026)
 */
public class RobotMessage {
    public static final int NUMBER_OF_JOINTS = 6;
    public float[] jointAngles = new float[NUMBER_OF_JOINTS];
    public float[] position = new float[3];

    /**
     * Creates a RobotMessage from an array of jointAngles and positions.
     * @param jointAngles
     * @param position
     */
    public RobotMessage(
            @JsonProperty("jointAngles") float[] jointAngles,
            @JsonProperty("position")    float[] position) {
    
        if (jointAngles == null || jointAngles.length != NUMBER_OF_JOINTS) {
            throw new IllegalArgumentException(
                "jointAngles must contain exactly " + NUMBER_OF_JOINTS + " values");
        }
        if (position == null || position.length != 3) {
            throw new IllegalArgumentException("position must contain exactly 3 elements");
        }
        this.jointAngles = jointAngles.clone();
        this.position = position.clone();
    }

    /**
     * Creates a deep copy of the provided RobotMessage.
     * @param rm The RobotMessage to be copied.
     */
    RobotMessage(RobotMessage rm){
        jointAngles = rm.jointAngles.clone();
        position = rm.position.clone();
    }

    /**
     * Parses a string into a RobotMessage. String should be of the form "ROBOT,J1,J2,J3,J4,J5,J6,X,Y,Z"
     * @param message The string to be parsed.
     */
    RobotMessage(String message){
        // expect: "ROBOT,J1,J2,J3,J4,J5,J6,X,Y,Z"
        if (!message.startsWith("ROBOT")){
            throw new Error("message is not ROBOT.");
        }

        String[] parts = message.split(",");
        // expect: [ROBOT, J1, J2, J3, J4, J5, J6, X, Y, Z]
        if (parts.length != 10){
            throw new Error("ROBOT message has unexpected amount of data. Expected 10 parts, recieved " + parts.length + ".");
        }

        // Parse joint angles.
        for (int i=0; i<6; ++i){
            jointAngles[i] = Float.parseFloat(parts[i+1]);
        }
        // Parse position.
        for (int i=0; i<3; ++i){
            position[i] = Float.parseFloat(parts[i+7]);
        }
    }

    /**
     * Creates a deep copy of the RobotMessage
     * @see java.lang.Object#clone()
     */
    public RobotMessage clone(){
        return new RobotMessage(this); 
    }

    /**
     * Provides a descriptive and formatted string of the data.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROBOT:\n\tAngles: ");
        for (float angle : jointAngles) sb.append(angle + ",");
        sb.append("\n\tPosition:");
        sb.append(" x=" + position[0] + ", y=" + position[1] + ", z=" + position[2] + "\n");
        return sb.toString();
    } 
}
