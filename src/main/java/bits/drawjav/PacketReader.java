/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.*;

/**
 * @author decamp
 */
public interface PacketReader extends Channel {
    public int streamCount();
    public Stream stream( int index );
    public List<Stream> streams();
    public void openStream( Stream stream ) throws IOException;
    public void closeStream( Stream stream ) throws IOException;
    public boolean isStreamOpen( Stream stream );

    public void seek( long micros ) throws IOException;

    /**
     * @return The next packet, or possibly {@code null}. It may take multiple calls to receive a packet.
     * @throws java.io.EOFException if no more packets are available.
     * @throws java.io.IOException on other exceptions.
     */
    public Packet readNext() throws IOException;

    public void close() throws IOException;
    public boolean isOpen();
}
