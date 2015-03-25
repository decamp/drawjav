package bits.drawjav;

import bits.jav.Jav;


/**
 * @author Philip DeCamp
 */
public class PoolMemoryManager implements MemoryManager {

    private MultiFormatAllocator mVideoMem;
    private MultiFormatAllocator mAudioMem;


    public PoolMemoryManager( int audioItemCap,
                              int audioByteCap,
                              int videoItemCap,
                              int videoByteCap )
    {
        if( videoItemCap > 0 || videoByteCap < 0 ) {
            mVideoMem = MultiFormatAllocator.createPacketLimited( videoItemCap );
        } else {
            mVideoMem = MultiFormatAllocator.createByteLimited( videoByteCap );
        }

        if( audioItemCap > 0 || audioByteCap < 0 ) {
            mAudioMem = MultiFormatAllocator.createPacketLimited( audioItemCap );
        } else {
            mAudioMem = MultiFormatAllocator.createByteLimited( audioByteCap );
        }
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
