package bits.drawjav.pipe;

import java.nio.channels.Channel;


/**
 * @author Philip DeCamp
 */
public interface Filter extends Channel {
    int sinkPadNum();
    SinkPad sinkPad( int idx );
    int sourcePadNum();
    SourcePad sourcePad( int idx );
    void clear();
}
