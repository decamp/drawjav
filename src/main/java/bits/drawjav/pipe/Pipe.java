package bits.drawjav.pipe;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.List;


/**
 * @author Philip DeCamp
 */
public interface Pipe<T> extends Channel {
    public int process( T packet, List<? super T> out ) throws IOException;
    public void clear();

}
