package bits.drawjav.pipe;

import bits.drawjav.Packet;


/**
 * @author Philip DeCamp
 */
public interface SinkPad<T extends Packet> {
    FilterErr offer( T packet, long blockMicros );
    int available();
    Exception exception();
}
