import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Transmitter {

    private static final int DEFAULT_PAYLOAD_SIZE_IN_BYTES = 25; // prevents fragmentation
    private static final int DEFAULT_SERVER_PORT = 12345;
    private static final String DEFAULT_SERVER = "127.0.0.1";
    private static final int SEQ_NO_BYTES = 4;
    private static final int DATA_LENGTH_BYTES = 2;
    private static final int CRC32_LENGTH_BYTES = 8; // java has no unsigned int -> long

    private final int serverPort;
    private final int payloadSize;
    private final String serverName;

    public Transmitter() {
        this(DEFAULT_PAYLOAD_SIZE_IN_BYTES, DEFAULT_SERVER_PORT, DEFAULT_SERVER);
    }

    public Transmitter(final int payloadSize) {
        this(payloadSize, DEFAULT_SERVER_PORT, DEFAULT_SERVER);
    }

    public Transmitter(final int payloadSize, final int serverPort) {
        this(payloadSize, serverPort, DEFAULT_SERVER);
    }

    public Transmitter(final int payloadSize, final int serverPort, final String serverName) {
        this.payloadSize = payloadSize;
        this.serverPort = serverPort;
        this.serverName = serverName;
    }

    public void send(final String message) throws IOException {

        final DatagramSocket socket = new DatagramSocket();
        final byte[] transferData = message.getBytes(StandardCharsets.UTF_8);

        // send all data packets
        {
            final int chunkSize = this.payloadSize - 6; // 4 byte seq no & 2 bytes data size
            final int noOfNeededPackets = (int) Math.ceil(transferData.length / (double) chunkSize);

            int startPointer = 0;
            for(int i=1; i<=noOfNeededPackets; i++) {

                int endPointer = startPointer + chunkSize;
                if(noOfNeededPackets == i) {
                    // trim last packet
                    endPointer = startPointer + transferData.length % chunkSize;
                }

                final byte[] chunk = Arrays.copyOfRange(transferData, startPointer, endPointer);

                sendUntilConfirmed(socket, ByteBuffer.allocate(SEQ_NO_BYTES + DATA_LENGTH_BYTES + chunk.length).order(ByteOrder.BIG_ENDIAN)
                        .putInt(i) // seq no
                        .putShort((short) chunk.length) // data length
                        .put(chunk) // data
                        .array());

                // advance in transferData
                startPointer = startPointer + chunkSize;
            }
        }

        // send CRC packet (finishes transfer)
        {
            final Checksum checksum = new CRC32();
            checksum.update(transferData, 0, transferData.length);
            sendUntilConfirmed(socket, ByteBuffer.allocate(SEQ_NO_BYTES + CRC32_LENGTH_BYTES).order(ByteOrder.BIG_ENDIAN)
                    .putInt(0) // seq number 0
                    .putLong(checksum.getValue()) // crc32
                    .array());
        }

        // clean up
        socket.close();
    }

    private void sendUntilConfirmed(final DatagramSocket socket, final byte[] data) throws IOException {
        boolean send = true;

        // wait max 250ms before trying again (this is the bottleneck)
        socket.setSoTimeout(250);

        while (send) {
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(this.serverName), this.serverPort));

            try {
                socket.receive(new DatagramPacket(ByteBuffer.allocate(4).array(), 4));
                send = false;

            } catch (SocketTimeoutException e){
                System.out.println("> No Ack received. Sending packet again.");
            }
        }
    }


    public static void main(String[] args) throws Exception {

        final Transmitter transmitter;
        String message = "THIS IS A REALLY AWESOME TEST MESSAGE (öüä)!";

        // quick and dirty, better would be Apache Commons CLI
        {
            if (args.length == 4) {
                transmitter = new Transmitter(Integer.valueOf(args[1]), Integer.valueOf(args[2]), args[3]);
            } else if (args.length == 3) {
                transmitter = new Transmitter(Integer.valueOf(args[1]), Integer.valueOf(args[2]));
            } else if (args.length == 2) {
                transmitter = new Transmitter(Integer.valueOf(args[1]));
            } else {
                transmitter =  new Transmitter();
            }

            if(args.length > 0) {
                message = args[0];
            }
        }

        // send message to server
        {
            System.out.println("> Sending message: " + message);

            final long startTimestamp = System.currentTimeMillis();
            transmitter.send(message);
            final long duration = System.currentTimeMillis() - startTimestamp;

            System.out.println("> Transfer done! Duration: " + duration + "ms");
        }
    }


}