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
    public StreamHandle stream( int index );
    public List<StreamHandle> streams();
    public void openStream( StreamHandle stream ) throws IOException;
    public void closeStream( StreamHandle stream ) throws IOException;
    public boolean isStreamOpen( StreamHandle stream );

    public void seek( long micros ) throws IOException;
    public Packet readNext() throws IOException;

    public void close() throws IOException;
    public boolean isOpen();
}
