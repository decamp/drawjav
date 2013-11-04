package cogmac.drawjav;

import java.nio.channels.Channel;
import bits.clocks.PlayController;

/**
 * @author decamp
 */
public interface MultiSourceDriver extends Channel, StreamFormatter {
    public void start();
    public boolean addSource(Source source);
    public boolean removeSource(Source source);
    public PlayController playController();
}
