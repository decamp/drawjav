package bits.drawjav;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.*;

import bits.util.Guid;

/**
 * @author decamp
 */
public interface Source extends Channel {
    public int streamCount();
    public StreamHandle stream( int index );
    public List<StreamHandle> streams();

    public void openStream( StreamHandle stream ) throws IOException;
    public void closeStream( StreamHandle stream ) throws IOException;
    public void seek( long micros ) throws IOException;
    public Packet readNext() throws IOException;
    
    public void close() throws IOException;
    public boolean isOpen();
}
