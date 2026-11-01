package edu.calpoly.lambdalab.paulmotter;

import java.util.Map;

public class RobotDistanceLambdaData {
    public float x1, y1, z1, x2, y2, z2, distance = -1.0f;
    public String id = null;


    public static RobotDistanceLambdaData fromParams(Map<String, Object> params){
        if (params == null) throw new IllegalArgumentException();
        RobotDistanceLambdaData data = new RobotDistanceLambdaData();
        data.x1 = checkMapAndParseFloat(params,"x1");
        data.y1 = checkMapAndParseFloat(params,"y1");
        data.z1 = checkMapAndParseFloat(params,"z1");
        data.x2 = checkMapAndParseFloat(params,"x2");
        data.y2 = checkMapAndParseFloat(params,"y2");
        data.z2 = checkMapAndParseFloat(params,"z2");
        return data;
    }

    private static float checkMapAndParseFloat(Map<String, Object> map, String key){
        if (map.containsKey(key)){
            // Query parameters arrive as Strings; JSON body numbers arrive as Integer/Double.
            return Float.parseFloat(String.valueOf(map.get(key)));
        } else {
            throw new IllegalArgumentException();
        }
    }

}
