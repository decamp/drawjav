package bits.drawjav;

import bits.jav.codec.JavPacket;


/**
 * @author Philip DeCamp
 */
public interface JavPacketAllocator {
    public JavPacket alloc( int size );
}
