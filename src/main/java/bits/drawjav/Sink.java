/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.Channel;

/**
 * @author decamp
 */
public interface Sink<T> extends Channel {
    /**
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
