package org.uav.queue;

import org.uav.config.Configuration;
import org.uav.model.Drone;
import org.uav.parser.DroneRequestReplyParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Optional;

public class DroneRequester {

    private static final String port = "9000";
    private static final String TAKEN_MESSAGE = "taken";
    private final ZMQ.Socket socket;
    private final ZContext context;
    private final DroneRequestReplyParser messageParser;

    public DroneRequester(ZContext context, Configuration configuration) {
        this.context = context;
        String address = "tcp://" + configuration.address + ":" + port;
        messageParser = new DroneRequestReplyParser();
        socket = context.createSocket(SocketType.REQ);
        socket.connect(address);
    }

    public Optional<Drone> requestNewDrone(String droneName) {
        socket.send(droneName.getBytes(ZMQ.CHARSET), 0);
        byte[] reply = socket.recv();
        String message = new String(reply, ZMQ.CHARSET);

        if(message.equals(TAKEN_MESSAGE))
            return Optional.empty();

        var parsedMessage = messageParser.parse(message);

        return Optional.of(new Drone(context, parsedMessage.dronePort, parsedMessage.droneId));
    }
}
