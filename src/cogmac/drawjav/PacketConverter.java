package cogmac.drawjav;

import java.io.*;


public interface PacketConverter<T> extends Closeable {
    public T convert( T packet ) throws IOException;
    public T drain() throws IOException;
    public void clear();
    public void close() throws IOException;
}
