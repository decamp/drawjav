package cogmac.javdraw;

import java.nio.channels.Channel;
import cogmac.clocks.PlayController;

/**
 * @author decamp
 */
public interface MultiSourceDriver extends Channel, StreamFormatter {
    public boolean addSource(Source source);
    public boolean removeSource(Source source);
    
    public PlayController playController();
}
