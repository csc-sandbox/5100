## Robot Feature Evolution

### Long Term Goals

The `RobotGUI.java` will likely become partially REST and partially MQTT. The overall UI will likely come from a REST endpoint ensuring reliable loading of the overall page. The real-time updates to data will come from an MQTT topic that ensures quick low-latency updates.

I believe `RobotMessage.java` and `RobotBlackBoard.java` can be used by other parts of the app as a libarary/API. This is because they are targetted more towards general data management and validation.

### Short Term Goals

- I believe extracting some interfaces from `RobotDataPanel.java`, `RobotDisplayPanel.java`, and `RobotGUI.java` would be useful for other GUI displays for other devices like a Rumba or the eye tracking.
- Similarly extracting `RobotBlackBoard` to an interface could be helpful or making it a generic for the storage and callback. Additionally making it be able to store multiple callbacks and possibly a history of messages would be useful.
- Might be out of scope, but drawing higher resolution visualizations is very demanding. Extracting those visualizations to a GPU based rendering method would help with performance and allow increased image quality.
- Another idea for expansion is allowing users to send commands to a robot and have the visualization update as the robot recieves and moves the commanded position.
- Having global variables for UI elements like fonts, colors, etc. would be nice for keeping the project unfiform.

## Contract

**`RobotGUI.java`**

- **Inputs**: are `RobotMessage` objects.
- **Ouput**: is a visualization of the data and it's printed values. These can be added to a `JFrame`
- **Dependencies**: `RobotMessage` objects as inputs. `RobotDataPanel` to control the data dispaly. `RobotDisplayPanel` to control the robot drawing/visulaization. `JPanel` used to organize the two other GUI panels.

**`RobotDatapanel.java`**

- **Inputs**: are `RobotMessage` and `Date` objects.
- **OutPuts**: A GUI printing out the state/values of RobotMessage.
- **Dependencies**: `RobotMessage` for the inputs and inherits from `JPanel` and a couple other Java Swing components.

**`RobotDisplayPanel.java`**

- **Inputs**: Are `RobotMessage` objects.
- **OutPuts**: A GUI displaying the robot's state.
- **Dependencies**: `BufferedImage` is used to store and create the image. Inherits from `JPanel` for JSwing compatabilities.

**`RobotMessage.java`**

- **Inputs**: are MQTT messages in the "ROBOT" format.
- **Ouput**: is an `RobotMessage` object.
- **Dependencies**: No direct dependencies.

**`RobotBlackBoard`**

- **Inputs**: are `RobotMessage` objects.
- **Ouputs**: are calls to callback functions that are passed the current `RobotMessage` and the ability to retrieve a current state.
- **Dependencies**: `RobotMessage` as the data that is posted, retrieved, and passed to a callback.

**`RobotRESTAdapter`**

- **inputs**: GET and PUT request to /robot/\<robotID>
- **Outputs**: Updates a `RobotBlackBoard`
- **Dependencies**: `RobotBlackBoard` and `HttpServer`.

**`RobotMQTTAdapter`**

- **inputs**: Published packages to csc5100/robot/\<robotID>
- **Outputs**: Updates a `RobotBlackBoard`
- **Dependencies** `RobotBlackBoard` and `MqttClient`

**`RobotRESTProvider`**

- **inputs**: Strings and RobotMessages to be sent.
- **Outputs**: Sent requests to the /robot/\<robotID> resource.
- **Dependencies** `HttpClient`

**`RobotRESTProvider`**

- **inputs**: Strings and RobotMessages to be sent.
- **Outputs**: Publishing packages on the csc510/robot/\<robotID> topic.
- **Dependencies** `MqttClient`
