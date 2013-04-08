package cogmac.javdraw;

import java.nio.channels.Channel;

import cogmac.clocks.PlayController;

/**
 * @author decamp
 */
public interface StreamDriver extends Channel, StreamFormatter {
    public Source source();
    public PlayController playController();
}
