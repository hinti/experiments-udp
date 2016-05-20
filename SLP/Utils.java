import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

class Utils {


    private static final int UDP_HEADER_SIZE_IN_BYTES = 8;
    /* 4 seqNo + 4 data length + 4 crc */
    private static final int OWN_PROTOCOL_HEADER_AND_TRAILER_SIZE_IN_BYTES = 12;

    static final int MAX_DATAGRAM_SIZE_IN_BYTES = 30000;
    /* total size under 512 byte prevents fragmentation */
    static final int DEFAULT_DATAGRAM_SIZE_IN_BYTES = 512 - UDP_HEADER_SIZE_IN_BYTES - OWN_PROTOCOL_HEADER_AND_TRAILER_SIZE_IN_BYTES;
    static final int DEFAULT_WINDOW_SIZE = 8;

    static final int DEFAULT_SERVER_PORT = 12345;
    static final String DEFAULT_SERVER_ADDRESS = "127.0.0.1";

    static byte[] createPacket(final int seqNo, final byte[] data) {

        /*
        * 4 byte seqNo | 4 byte data length | ? bytes chunk of data | 4 byte crc 32
        */

        final byte[] packetWithoutCRC = ByteBuffer.allocate(data.length + 8)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(seqNo)
                .putInt(data.length)
                .put(data)
                .array();

        return ByteBuffer.allocate(packetWithoutCRC.length + 4)
                .order(ByteOrder.BIG_ENDIAN)
                .put(packetWithoutCRC)
                .put(generateCrc32(packetWithoutCRC))
                .array();

    }

    static byte[] extractPacket(final DatagramPacket dp) {
        ByteBuffer bb = ByteBuffer.wrap(dp.getData()).order(ByteOrder.BIG_ENDIAN);
        bb.getInt(); // skip sequence number
        return Arrays.copyOfRange(dp.getData(), 0, bb.getInt() + OWN_PROTOCOL_HEADER_AND_TRAILER_SIZE_IN_BYTES);
    }

    static int getPacketSlot(final byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    static byte[] getPacketData(final byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        bb.getInt(); // skip sequence number
        return Arrays.copyOfRange(data, 8, 8 + bb.getInt());
    }

    static boolean isEndPacket(final byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        bb.getInt();
        return bb.getInt() == 0; // 0 length packet indicates last packet of message
    }

    static boolean isPacketValid(final byte[] data) {
        return Arrays.equals(
                generateCrc32(Arrays.copyOfRange(data, 0, data.length-4)),
                Arrays.copyOfRange(data, data.length-4, data.length));
    }

    private static byte[] generateCrc32(final byte[] data) {
        Checksum checksum = new CRC32();
        checksum.update(data, 0, data.length);
        byte[] bytes = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(checksum.getValue()).array();
        return Arrays.copyOfRange(bytes, 4, 8);
    }

}
