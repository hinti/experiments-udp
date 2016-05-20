import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Receiver {

    private final int port;

    Receiver() {
        this(Utils.DEFAULT_SERVER_PORT);
    }

    Receiver(final int port) {
        this.port = port;
    }

    private void start() throws IOException {

        System.out.println("### Receiver started!");
        System.out.println("### Port: " + this.port);

        // ctrl-c
        final boolean[] running = {true};
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutting down receiver.");
                running[0] = false;
            }
        });

        final DatagramSocket socket = new DatagramSocket(this.port);
        List<byte[]> receivedPackets = new ArrayList<>();
        int nextSequenceNumber = 0;

        while (running[0]) {

            // receive next packet
            final DatagramPacket dp = new DatagramPacket(ByteBuffer.allocate(Utils.MAX_DATAGRAM_SIZE_IN_BYTES).array(), Utils.MAX_DATAGRAM_SIZE_IN_BYTES);
            socket.receive(dp);
            final byte[] packet = Utils.extractPacket(dp);

            // expected packet received
            if(Utils.getPacketSlot(packet) == nextSequenceNumber || Utils.isPacketValid(packet)) {

                if(Utils.isEndPacket(packet)) {

                    nextSequenceNumber = 0;

                    // concatenate data packets
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    for (byte[] chunk : receivedPackets) {
                        data.write(chunk);
                    }

                    System.out.println("Message received: " + data.toString(StandardCharsets.UTF_8.name()));

                } else {

                    receivedPackets.add(Utils.getPacketData(packet));
                    nextSequenceNumber++;
                }

            }

            // send answer with next expected slot number
            final byte[] answerPacket = Utils.createPacket(nextSequenceNumber, "ACK".getBytes());
            socket.send(new DatagramPacket(answerPacket, answerPacket.length, dp.getAddress(), dp.getPort()));

        }

        socket.close();
    }

    public static void main(String[] args) throws Exception {
        new Receiver().start();
    }

}