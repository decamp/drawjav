/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.*;
import bits.drawjav.video.*;


/**
 * @author Philip DeCamp
 */

public class PoolPerFormatMemoryManager implements MemoryManager {

    private final int mAudioItemCap;
    private final int mAudioByteCap;
    private final int mVideoItemCap;
    private final int mVideoByteCap;

    public PoolPerFormatMemoryManager( int audioItemCap,
                                       int audioByteCap,
                                       int videoItemCap,
                                       int videoByteCap )
    {
        mAudioItemCap = audioItemCap;
        mAudioByteCap = audioByteCap;
        mVideoItemCap = videoItemCap;
        mVideoByteCap = videoByteCap;
    }


    @Override
    public VideoAllocator videoAllocator( StreamHandle stream ) {
        if( mVideoItemCap > 0 || mVideoByteCap <= 0 ) {
            return OneFormatVideoAllocator.createPacketLimited( mVideoItemCap );
        } else {
            return OneFormatVideoAllocator.createByteLimited( mVideoByteCap );
        }
    }

    @Override
    public AudioAllocator audioAllocator( StreamHandle stream ) {
        if( mAudioItemCap > 0 || mAudioByteCap <= 0 ) {
            return OneFormatAudioAllocator.createPacketLimited( mAudioItemCap, -1 );
        } else {
            return OneFormatAudioAllocator.createByteLimited( mAudioByteCap, -1 );
        }
    }
}
