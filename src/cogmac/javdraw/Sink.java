package cogmac.javdraw;

import java.io.*;

/**
 * @author decamp
 */
public interface Sink<T> extends Closeable {
    public void consume(T packet) throws IOException;
    public void clear();
    public void close() throws IOException;
}
