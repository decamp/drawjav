package bits.drawjav;

import bits.drawjav.audio.AudioAllocator;
import bits.drawjav.audio.MultiFormatAudioAllocator;
import bits.drawjav.video.MultiFormatVideoAllocator;
import bits.drawjav.video.VideoAllocator;


/**
 * @author Philip DeCamp
 */
public class PoolMemoryManager implements MemoryManager {

    private MultiFormatVideoAllocator mVideoMem;
    private MultiFormatAudioAllocator mAudioMem;


    public PoolMemoryManager( int audioItemCap,
                              int audioByteCap,
                              int videoItemCap,
                              int videoByteCap )
    {
        if( videoItemCap > 0 || videoByteCap < 0 ) {
            mVideoMem = MultiFormatVideoAllocator.createPacketLimited( videoItemCap );
        } else {
            mVideoMem = MultiFormatVideoAllocator.createByteLimited( videoByteCap );
        }

        if( audioItemCap > 0 || audioByteCap < 0 ) {
            mAudioMem = MultiFormatAudioAllocator.createPacketLimited( audioItemCap );
        } else {
            mAudioMem = MultiFormatAudioAllocator.createByteLimited( audioByteCap );
        }
    }

    @Override
    public VideoAllocator videoAllocator( StreamHandle stream ) {
        mVideoMem.ref();
        return mVideoMem;
    }

    @Override
    public AudioAllocator audioAllocator( StreamHandle stream ) {
        mAudioMem.ref();
        return mAudioMem;
    }

}
