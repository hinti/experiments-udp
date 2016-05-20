import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Transmitter {

    private final int serverPort;
    private final String serverName;

    Transmitter() {
        this(Utils.DEFAULT_SERVER_PORT, Utils.DEFAULT_SERVER_ADDRESS);
    }

    Transmitter(final int serverPort, final String serverName) {
        this.serverPort = serverPort;
        this.serverName = serverName;
    }

    private void send(final String message) throws IOException {

        final DatagramSocket socket = new DatagramSocket();
        final List<byte[]> data = convertString(message);

        int sequenceNumber = 0;
        boolean transferRunning = true;

        socket.setSoTimeout(500); // worst case RTT

        while(transferRunning) {

            // send out window
            {
                int currentSeq = sequenceNumber;
                for(int i=0; i < Utils.DEFAULT_WINDOW_SIZE; i++) {
                    if(data.size() > currentSeq) {
                        byte[] payload = Utils.createPacket(currentSeq, data.get(currentSeq));
                        socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(this.serverName), this.serverPort));
                        currentSeq++;
                    }
                }
            }

            // get next sequence number
            try {

                DatagramPacket dp = new DatagramPacket(ByteBuffer.allocate(Utils.MAX_DATAGRAM_SIZE_IN_BYTES).array(), Utils.MAX_DATAGRAM_SIZE_IN_BYTES);
                socket.receive(dp);
                final byte[] packet = Utils.extractPacket(dp);
                final int nextSequenceNumber = Utils.getPacketSlot(packet);

                if(nextSequenceNumber > sequenceNumber) {
                    sequenceNumber = nextSequenceNumber;
                }

            } catch (SocketTimeoutException e) { /* ignore */ }

            // all done?
            if(data.size() == sequenceNumber) {
                transferRunning = false;
                byte[] payload = Utils.createPacket(sequenceNumber, new byte[]{});
                socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(this.serverName), this.serverPort));
            }

        }
    }

    private List<byte[]> convertString(final String message) {

        final byte[] transferData = message.getBytes(StandardCharsets.UTF_8);
        final int noOfNeededPackets = (int) Math.ceil(transferData.length / (double) Utils.DEFAULT_DATAGRAM_SIZE_IN_BYTES);
        final List<byte[]> result = new ArrayList<>();

        int startPointer = 0;
        for(int i=1; i <= noOfNeededPackets; i++) {
            int endPointer = startPointer + Utils.DEFAULT_DATAGRAM_SIZE_IN_BYTES;
            if (noOfNeededPackets == i) {
                // trim last packet
                endPointer = startPointer + transferData.length % Utils.DEFAULT_DATAGRAM_SIZE_IN_BYTES;
            }
            result.add(Arrays.copyOfRange(transferData, startPointer, endPointer));
            startPointer = startPointer + Utils.DEFAULT_DATAGRAM_SIZE_IN_BYTES;
        }

        return result;
    }

    public static void main(String[] args) throws Exception {

        String message = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum imperdiet non metus id sodales. " +
                "Quisque sed ullamcorper magna, quis lacinia elit. Class aptent taciti sociosqu ad litora torquent " +
                "per conubia nostra, per inceptos himenaeos. Cum sociis natoque penatibus et magnis dis parturient " +
                "montes, nascetur ridiculus mus. Maecenas sit amet velit nec nisl imperdiet dapibus a at lectus. " +
                "Maecenas ac libero sem. Suspendisse sapien libero, dictum id erat a, suscipit laoreet elit. " +
                "Sed fringilla, risus eu pellentesque aliquam, augue magna pharetra metus, eget aliquam ipsum ipsum eget nulla. " +
                "Quisque commodo tincidunt dui a dapibus. Fusce aliquam nisi ut orci vulputate porta. " +
                "Integer quis quam vel neque tempor porta. In non vestibulum metus.";

        if(args.length == 1) {
            message = args[0];
        }

        // send message to server

        System.out.println("> Sending test message 2x");
        Transmitter transmitter = new Transmitter();

        final long startTimestamp = System.currentTimeMillis();
        transmitter.send(message);
        transmitter.send(message);
        final long duration = System.currentTimeMillis() - startTimestamp;

        System.out.println("> Transfer done! Duration: " + duration + "ms");

    }

}
