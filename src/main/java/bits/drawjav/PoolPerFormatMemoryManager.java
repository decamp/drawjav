/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.jav.Jav;


/**
 * @author Philip DeCamp
 */

public class PoolPerFormatMemoryManager implements MemoryManager {

    private final int mAudioItemCap;
    private final int mVideoItemCap;

    public PoolPerFormatMemoryManager( int audioItemCap, int videoItemCap ) {
        mAudioItemCap = audioItemCap;
        mVideoItemCap = videoItemCap;
    }

    @Override
    public PacketAllocator<DrawPacket> allocator( StreamFormat stream ) {
        switch( stream.mType ) {
        case Jav.AVMEDIA_TYPE_AUDIO:
            return OneFormatAllocator.createPacketLimited( mAudioItemCap );

        case Jav.AVMEDIA_TYPE_VIDEO:
            return OneFormatAllocator.createPacketLimited( mVideoItemCap );
        }

        return null;
    }

}
