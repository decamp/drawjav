package bits.drawjav.pipe;

import java.nio.channels.Channel;


/**
 * @author Philip DeCamp
 */
public interface Filter extends Channel {
    int sinkNum();
    SinkPad sink( int idx );
    int sourceNum();
    SourcePad source( int idx );
    void clear();
}
