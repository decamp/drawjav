package bits.drawjav.pipe;

import java.nio.channels.Channel;
import com.google.common.eventbus.EventBus;

/**
 * @author Philip DeCamp
 */
public interface AvUnit extends Channel {
    int inputNum();
    InPad input( int idx );
    int outputNum();
    OutPad output( int idx );

    void open( EventBus bus );
    void close();
    boolean isOpen();
    void clear();
}
