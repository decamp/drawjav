package cogmac.drawjav;

import java.io.*;
import java.nio.channels.Channel;

/**
 * @author decamp
 */
public interface Sink<T> extends Channel {
    /**
     * @param packet
     * @throws InterruptedIOException if interruption occurs before packet was consumed.
     * @throws IOException on other errors.
     */
    public void consume( T packet ) throws IOException;
    
    /**
     * Clears any state in sink.
     */
    public void clear();
    
    /**
     * Closes sink.
     */
    public void close() throws IOException;
    
}
