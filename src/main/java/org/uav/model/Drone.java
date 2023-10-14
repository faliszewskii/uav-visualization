package org.uav.model;

import lombok.Getter;
import org.uav.config.AvailableControlModes;
import org.uav.config.Config;
import org.uav.model.controlMode.ControlModeDemanded;
import org.uav.model.controlMode.ControlModeReply;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.uav.utils.ZmqUtils.checkErrno;

public class Drone {

    @Getter
    private final int id;
    private final ZMQ.Socket steerSocket;
    private final ZMQ.Socket utilsSocket;
    private final SimulationState simulationState;
    private final AvailableControlModes availableControlModes;

    public Drone(
            ZContext context,
            int steerPort,
            int utilsPort,
            int droneId,
            SimulationState simulationState,
            Config config,
            AvailableControlModes availableControlModes
    ) {
        this.simulationState = simulationState;
        this.availableControlModes = availableControlModes;
        id = droneId;

        steerSocket = context.createSocket(SocketType.REQ);
        String address = "tcp://" + config.getServerSettings().getServerAddress() + ":" + steerPort;
        steerSocket.setReceiveTimeOut(config.getServerSettings().getServerTimoutMs());
        steerSocket.setSendTimeOut(config.getServerSettings().getServerTimoutMs());
        steerSocket.connect(address);

        utilsSocket = context.createSocket(SocketType.PAIR);
        String address2 = "tcp://" + config.getServerSettings().getServerAddress() + ":" + utilsPort;
        utilsSocket.setReceiveTimeOut(config.getServerSettings().getServerTimoutMs());
        utilsSocket.setSendTimeOut(config.getServerSettings().getServerTimoutMs());
        utilsSocket.connect(address2);
    }

    public void sendSteeringCommand(String command) {
        if(!steerSocket.send(command.getBytes(ZMQ.CHARSET), 0)) checkErrno(steerSocket);
        byte[] reply = steerSocket.recv(0);
        if(reply == null) checkErrno(steerSocket);
        String message = new String(reply, ZMQ.CHARSET);
        parseSteeringCommand(message);
    }

    private void parseSteeringCommand(String message) {
        int commaIdx = message.indexOf(',');
        commaIdx = commaIdx == -1? message.length(): commaIdx;
        String mode = message.substring(0, commaIdx);
        if (!mode.equals("ok"))
            simulationState.setCurrentControlModeDemanded(parseControlModeMessage(mode, message.substring(commaIdx)));
    }

    private ControlModeDemanded parseControlModeMessage(String mode, String message) {
        Scanner scanner = new Scanner(message);
        scanner.useDelimiter(",");
        List<ControlModeReply> replyList = availableControlModes.getModes().get(mode).getReply();
        if(replyList == null)
            return new ControlModeDemanded(mode, new HashMap<>());
        Map<ControlModeReply, Float> demanded = replyList.stream()
                .collect(Collectors.toMap(Function.identity(), e -> Float.parseFloat(scanner.next())));
        return new ControlModeDemanded(mode, demanded);
    }

    public String sendUtilsCommand(String command) {
        if(!utilsSocket.send(command.getBytes(ZMQ.CHARSET), 0)) checkErrno(utilsSocket);
        byte[] reply = utilsSocket.recv(0);
        if(reply == null) checkErrno(utilsSocket);
        return new String(reply, ZMQ.CHARSET);
    }
}
