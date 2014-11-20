package bits.drawjav.video;

import bits.util.ref.ObjectPool;
import bits.util.ref.Refable;

/**
 * Like an ObjectPool, but with additional method that
 * will allocate object if none is available in pool.
 *
 * @author Philip DeCamp
 */
public interface VideoAllocator extends ObjectPool<VideoPacket>, Refable {
    public VideoPacket alloc( PictureFormat format );
}
