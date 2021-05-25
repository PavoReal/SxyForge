package xyz.gapeac.sxyforgemod.network;

import javafx.util.Pair;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UUIDLifePair extends Pair<UUID, Integer>
{
    public UUIDLifePair(UUID key, Integer value)
    {
        super(key, value);
    }

    public static byte[] GetBytesFromUUID(UUID id)
    {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(id.getMostSignificantBits());
        bb.putLong(id.getLeastSignificantBits());

        return bb.array();
    }

    public static UUID GetUUIDFromBytes(byte[] bytes)
    {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long first    = bb.getLong();
        long second   = bb.getLong();

        return new UUID(first, second);
    }
}
