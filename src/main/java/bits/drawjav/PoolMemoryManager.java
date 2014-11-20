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

public class PoolMemoryManager implements MemoryManager {

    private final int mAudioItemCap;
    private final int mAudioByteCap;
    private final int mVideoItemCap;
    private final int mVideoByteCap;

    public PoolMemoryManager( int audioItemCap,
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
    public VideoAllocator videoAllocator( StreamHandle stream, PictureFormat format ) {
        return new OneStreamVideoAllocator( mVideoItemCap, mVideoByteCap );
    }

    @Override
    public AudioAllocator audioAllocator( StreamHandle stream, AudioFormat format ) {
        return new OneStreamAudioAllocator( mAudioItemCap, mAudioByteCap, -1 );
    }
}
