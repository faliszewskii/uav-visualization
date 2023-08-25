package org.uav.queue;

import org.uav.config.Config;
import org.uav.model.SimulationState;
import org.uav.model.status.DroneStatuses;
import org.uav.parser.DroneStatusMessageParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.uav.utils.ZmqUtils.checkErrno;

public class DroneStatusConsumer {

    private final DroneStatuses droneStatuses;
    private final ReentrantLock droneStatusMutex;
    private final ZMQ.Socket socket;
    private final DroneStatusMessageParser messageParser;
    private final Thread thread;


    public DroneStatusConsumer(ZContext context, SimulationState simulationState, Config config) {
        this.droneStatuses = simulationState.getDroneStatuses();
        this.droneStatusMutex = simulationState.getDroneStatusesMutex();
        String address = "tcp://" + config.getServerAddress() + ":" + config.getPorts().getDroneStatuses();
        messageParser = new DroneStatusMessageParser();
        socket = context.createSocket(SocketType.SUB);
        socket.setSendTimeOut(config.getServerTimoutMs());
        socket.setReceiveTimeOut(config.getServerTimoutMs());
        socket.connect(address);
        socket.subscribe("");
        thread = new PositionThread();
    }

    public void start() {
        if(!thread.isAlive())
            thread.start();
    }

    public void stop() {
        thread.interrupt();
    }

    class PositionThread extends Thread {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    byte[] reply = socket.recv(0);
                    if(reply == null) checkErrno(socket);
                    String message = new String(reply, ZMQ.CHARSET);
                    //System.out.println("Received: [" + message + "]");
                    droneStatusMutex.lock();
                    droneStatuses.map = messageParser.parse(message).stream()
                            .collect(Collectors.toMap(drone -> drone.id, Function.identity()));
                    droneStatusMutex.unlock();

                } catch (ZMQException exception) {
                    break;
                }
            }
        }
    }
}
