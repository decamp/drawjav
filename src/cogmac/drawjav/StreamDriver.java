package cogmac.drawjav;

import java.nio.channels.Channel;

import cogmac.clocks.PlayController;

/**
 * @author decamp
 */
public interface StreamDriver extends Channel, StreamFormatter {
    public void start();
    public Source source();
    public PlayController playController();
}
