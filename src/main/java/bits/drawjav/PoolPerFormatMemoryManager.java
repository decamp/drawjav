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
    public PacketAllocator<DrawPacket> allocator( StreamFormat stream ) {
        switch( stream.mType ) {
        case Jav.AVMEDIA_TYPE_AUDIO:
            if( mAudioItemCap > 0 || mAudioByteCap <= 0 ) {
                return OneFormatAllocator.createPacketLimited( mAudioItemCap, -1 );
            } else {
                return OneFormatAllocator.createByteLimited( mAudioByteCap, -1 );
            }

        case Jav.AVMEDIA_TYPE_VIDEO:
            if( mVideoItemCap > 0 || mVideoByteCap <= 0 ) {
                return OneFormatAllocator.createPacketLimited( mVideoItemCap, -1 );
            } else {
                return OneFormatAllocator.createByteLimited( mVideoByteCap, -1 );
            }
        }

        return null;
    }

}
