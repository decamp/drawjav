package bits.drawjav;

import bits.jav.Jav;


/**
 * @author Philip DeCamp
 */
public class PoolMemoryManager implements MemoryManager {

    private MultiFormatAllocator mVideoMem;
    private MultiFormatAllocator mAudioMem;


    public PoolMemoryManager() {
        this( 32, 16 );
    }


    public PoolMemoryManager( int audioItemCap, int videoItemCap ) {
        mVideoMem = MultiFormatAllocator.createPacketLimited( videoItemCap );
        mAudioMem = MultiFormatAllocator.createPacketLimited( audioItemCap );
    }

    @Override
    public PacketAllocator allocator( StreamFormat format ) {
        switch( format.mType ) {
        case Jav.AVMEDIA_TYPE_AUDIO:
            mAudioMem.ref();
            return mAudioMem;
        case Jav.AVMEDIA_TYPE_VIDEO:
            mVideoMem.ref();
            return mVideoMem;
        }

        return null;
    }

}
