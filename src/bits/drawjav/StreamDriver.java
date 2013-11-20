package bits.drawjav;

import java.nio.channels.Channel;
import bits.clocks.PlayController;

/**
 * @author decamp
 */
public interface StreamDriver extends Channel, StreamFormatter {
    public void start();
    public Source source();
    public PlayController playController();
}
