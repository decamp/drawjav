package bits.drawjav.pipe;

import bits.drawjav.Packet;
import java.io.IOException;


/**
 * @author Philip DeCamp
 */
public interface SourcePad<T extends Packet> {
    FilterErr remove( T[] out, long blockMicros  ) throws IOException;
    int available();
}
