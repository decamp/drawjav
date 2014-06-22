package bits.drawjav.audio;

import bits.util.ref.ObjectPool;
import bits.util.ref.Refable;


/**
 * Like an ObjectPool, but with additional method that
 * will allocate object if none is available in pool.
 *
 * @author Philip DeCamp
 */
public interface AudioAllocator extends ObjectPool<AudioPacket>, Refable {
    public AudioPacket alloc( AudioFormat format, int numSamples );
}
