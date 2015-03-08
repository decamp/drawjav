/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.util.logging.Logger;

import bits.drawjav.*;
import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;


/**
 * OneStreamVideoAllocator is a VideoPacket allocator that is optimized for a single
 * format of pictures. Attempts to allocate different formats with the same allocator instance
 * will produce poor performance and generate a warning.
 * <p>
 * OneStreamVideoAllocator uses a hard-referenced memory pool.
 *
 * @author Philip DeCamp
 */
public class OneFormatVideoAllocator extends AbstractRefable implements VideoAllocator {

    private static final Logger LOG = Logger.getLogger( OneFormatVideoAllocator.class.getName() );

    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static OneFormatVideoAllocator createPacketLimited( int maxPackets ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxPackets, maxPackets * 100, null );
        return new OneFormatVideoAllocator( pool );
    }


    public static OneFormatVideoAllocator createByteLimited( long maxBytes ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxBytes, maxBytes * 100, BYTE_COST );
        return new OneFormatVideoAllocator( pool );
    }



    private final CostPool<DrawPacket> mPool;

    private PictureFormat mPoolFormat;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;


    OneFormatVideoAllocator( CostPool<DrawPacket> pool ) {
        mPool = pool;
    }


    @Override
    public synchronized DrawPacket alloc( PictureFormat format ) {
        if( !checkFormat( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        DrawPacket packet = mPool.poll();
        if( packet != null ) {
            return packet;
        }

        if( format != null ) {
            try {
                packet = DrawPacket.createVideo( mPool, format );
            } catch( JavException ex ) {
                throw new RuntimeException( ex );
            }
        } else {
            packet = DrawPacket.createAuto( mPool );
        }

        mPool.allocated( packet );
        return packet;
    }

    @Override
    protected void freeObject() {
        mPool.close();
    }



    private boolean checkFormat( PictureFormat a, PictureFormat b ) {
        if( a == b ) {
            return true;
        }
        return a != null &&
               b != null &&
               a.width() == b.width() &&
               a.height() == b.height() &&
               a.pixelFormat() == b.pixelFormat();
    }


    private PictureFormat setPoolFormat( PictureFormat format ) {
        if( !PictureFormat.isFullyDefined( format ) ) {
            return mPoolFormat;
        }

        mPoolFormat = format;

        if( mHasFormat && !mHasChangedFormat ) {
            mHasChangedFormat = true;
            LOG.warning( getClass() + " is being used for multiple formats. Performance may be degraded." );
        }

        mPool.clear();
        return format;
    }

}

