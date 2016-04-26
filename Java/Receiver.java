import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


public class Receiver {

    private static final int DEFAULT_PAYLOAD_SIZE_IN_BYTES = 25; // prevent fragmentation
    private static final int DEFAULT_PORT = 12345;

    private final int payloadSize;
    private final int port;

    public Receiver() {
        this(DEFAULT_PORT, DEFAULT_PAYLOAD_SIZE_IN_BYTES);
    }

    public Receiver(final int port) {
        this(port, DEFAULT_PAYLOAD_SIZE_IN_BYTES);
    }

    public Receiver(final int port, final int payloadSize) {
        this.port = port;
        this.payloadSize = payloadSize;
    }

    private void start() throws IOException {

        System.out.println("### Receiver started!");
        System.out.println("### Port: " + this.port);
        System.out.println("### Max payload size per packet: " + this.payloadSize + " bytes");

        // ctrl-c
        final boolean[] running = {true};
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutting down receiver.");
                running[0] = false;
            }
        });

        final DatagramSocket socket = new DatagramSocket(this.port);
        Map<Integer, byte[]> receivedPackets = new TreeMap<>();

        while (running[0]) {

            // receive packet
            final DatagramPacket packet = new DatagramPacket(ByteBuffer.allocate(this.payloadSize).array(), this.payloadSize);
            socket.receive(packet);

            // process packet
            final ByteBuffer buffer = ByteBuffer.wrap(packet.getData()).order(ByteOrder.BIG_ENDIAN);
            final int seqNmb = buffer.getInt();

            if (seqNmb == 0) /* last packet */ {

                // concatenate data packets
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                for (Map.Entry<Integer, byte[]> entry : receivedPackets.entrySet()) {
                    data.write(entry.getValue());
                }

                // result of transfer
                if (isDataCorrect(buffer.getLong(), data)) {
                    System.out.println("> CRC OK! Received message: " + data.toString(StandardCharsets.UTF_8.name()));
                } else {
                    System.out.println("> CRC failed! Cannot display message.");
                }

                // clean up
                receivedPackets.clear();
                System.out.println("> Ready for new transfer");

            } else { /* standard packet */

                short dataLength = buffer.getShort();

                final byte[] b = new byte[dataLength];
                buffer.get(b);

                receivedPackets.put(seqNmb, b);

            }

            // always send ACK
            final byte[] ackData = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seqNmb).array();
            socket.send(new DatagramPacket(ackData, ackData.length, packet.getAddress(), packet.getPort()));

        }

        socket.close();
    }

    private boolean isDataCorrect(final long receivedCrc, final ByteArrayOutputStream data) {
        final byte[] byteData = data.toByteArray();
        final Checksum checksum = new CRC32();

        checksum.update(byteData, 0, byteData.length);

        return receivedCrc == checksum.getValue();
    }


    public static void main(String[] args) throws Exception {
        final Receiver receiver;

        // quick and dirty, better would be Apache Commons CLI
        if (args.length == 2) {
            receiver = new Receiver(Integer.valueOf(args[0]), Integer.valueOf(args[1]));
        } else if (args.length == 1) {
            receiver = new Receiver(Integer.valueOf(args[0]));
        } else {
            receiver =  new Receiver();
        }

        receiver.start();
    }
}