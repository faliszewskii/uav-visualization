package org.uav.input;

import org.uav.config.Configuration;
import org.uav.model.Drone;
import org.uav.queue.Actions;
import org.uav.queue.ControlModes;
import org.uav.model.status.JoystickStatus;
import org.uav.queue.JoystickProducer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static java.lang.Math.abs;
import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {
    public Configuration configuration;
    private final JoystickStatus joystickStatus;
    private final JoystickProducer joystickProducer;
    private final Drone drone;
    private boolean holdShoot = false;

    byte[] prevButtonsState = new byte[32];


    public InputHandler(Configuration configuration, Drone drone) {
        this.configuration = configuration;
        this.drone = drone;
        int joystickAxisCount = 4;
        joystickStatus = new JoystickStatus(joystickAxisCount);
        joystickProducer = new JoystickProducer();
    }

    public void processInput(long window) {
        // Camera Modes
        if (glfwGetKey(window, GLFW_KEY_0) == GLFW_PRESS)
            configuration.type = CameraType.FreeCamera;
        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS)
            configuration.type = CameraType.DroneCamera;
        if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS)
            configuration.type = CameraType.ObserverCamera;
        if (glfwGetKey(window, GLFW_KEY_3) == GLFW_PRESS)
            configuration.type = CameraType.RacingCamera;
        if (glfwGetKey(window, GLFW_KEY_4) == GLFW_PRESS)
            configuration.type = CameraType.HorizontalCamera;
        if (glfwGetKey(window, GLFW_KEY_5) == GLFW_PRESS)
            configuration.type = CameraType.HardFPV;
        if (glfwGetKey(window, GLFW_KEY_6) == GLFW_PRESS)
            configuration.type = CameraType.SoftFPV;

        int joystick = 0;
        while(!glfwJoystickPresent(joystick) && joystick < 10)
            joystick++;
        if(joystick == 10)
            System.out.println("[Error] No joystick found!");
        if(glfwJoystickPresent(joystick))
        {
            int count1 = 0;
            FloatBuffer floatBuffer = glfwGetJoystickAxes(joystick);
            //System.out.print("Axes:");

            while (floatBuffer != null && floatBuffer.hasRemaining()) {
                float axes = floatBuffer.get();
                //System.out.print(count1 + "," + axes + " ");
                if(configuration.joystickMapping.containsKey(count1))
                    joystickStatus.rawData[configuration.joystickMapping.get(count1)] = convertToRawData(count1, axes);
                if(configuration.axisActionsMapping.containsKey(count1))
                    handleAxis(configuration.axisActionsMapping.get(count1),axes);
                count1++;
            }
            //System.out.println("");

            ByteBuffer byteBuffer = glfwGetJoystickButtons(joystick);
            byte[] arr = new byte[byteBuffer.remaining()];
            byteBuffer.get(arr);
            handleButtons(arr);
            joystickProducer.send(drone, joystickStatus);
        }
    }

    private void handleButtons(byte[] buttonState) {

        for (int i = 0; i < buttonState.length; i++) {
            if(buttonState[i] == 0 || buttonState[i] == prevButtonsState[i]) continue;
            if(!configuration.joystickButtonsMapping.containsKey(i)) continue;
            switch(configuration.joystickButtonsMapping.getOrDefault(i,JoystickButtonFunctions.unused))
            {
                case nextCamera -> configuration.type = configuration.type.next();
                case prevCamera -> configuration.type = configuration.type.prev();
                case acroMode -> joystickProducer.send(drone, ControlModes.acro);
                case angleMode -> joystickProducer.send(drone, ControlModes.angle);
                case posMode -> joystickProducer.send(drone, ControlModes.pos);
                case noneMode -> joystickProducer.send(drone, ControlModes.none);
                case unused -> {}
            }
        }

        prevButtonsState = buttonState;
    }

    private void handleAxis(Actions action, float axisValue) {
        switch(action)
        {
            case shot ->
            {
                if(!holdShoot && axisValue > 0.5 ) {
                    holdShoot = true;
                    joystickProducer.send(drone, action);
                }
                if(holdShoot && axisValue < -0.5) {
                    holdShoot = false;
                }
            }
        }
    }

    private int convertToRawData(int index, float axes) {
        // axes is standardized to be in [-1,1]
        // Our standard requires [0,1024] and should take into account axis inversion
        Boolean inverted = configuration.joystickInversionMapping.get(index);
        return (int)((inverted? -1.0f: 1.0f) * deadZone(axes) * 512.0f + 512.0f);
    }

    private float deadZone(float axes) {
        return abs(axes) < configuration.deadZoneFactor ? 0.0f : axes;
    }
}
