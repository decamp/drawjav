package bits.drawjav.pipe;

import bits.drawjav.Packet;

import java.io.IOException;


/**
 * @author Philip DeCamp
 */
public interface SinkPad<T extends Packet> {
    FilterErr offer( T packet, long blockMicros ) throws IOException;
    boolean blocks();
    int available();
}
