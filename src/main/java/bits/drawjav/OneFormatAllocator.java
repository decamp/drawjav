/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.util.logging.Logger;

import bits.jav.Jav;
import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;
import com.google.common.base.Objects;


/**
 * @author Philip DeCamp
 */
public class OneFormatAllocator extends AbstractRefable implements PacketAllocator<DrawPacket> {

    private static final Logger LOG = Logger.getLogger( OneFormatAllocator.class.getName() );

    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static OneFormatAllocator createPacketLimited( int maxPackets, int defaultSampleNum ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxPackets, maxPackets * 100, null );
        return new OneFormatAllocator( pool, defaultSampleNum );
    }


    public static OneFormatAllocator createByteLimited( long maxBytes, int defaultSampleNum ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxBytes, maxBytes * 100, BYTE_COST );
        return new OneFormatAllocator( pool, defaultSampleNum );
    }


    private final CostPool<DrawPacket> mPool;

    private StreamFormat mPoolFormat;
    private int          mDefaultSize;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;


    OneFormatAllocator( CostPool<DrawPacket> pool, int defaultSize ) {
        mPool = pool;
        mDefaultSize = defaultSize;
    }


    public synchronized DrawPacket alloc( StreamFormat format ) {
        return alloc( format, mDefaultSize );
    }

    @Override
    public synchronized DrawPacket alloc( StreamFormat format, int size ) {
        if( !Objects.equal( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        DrawPacket ret = mPool.poll();

        if( ret != null ) {
            if( format == null || size < 0 ) {
                return ret;
            }

            // Check if packet can hold requested number of samples.
            // This calculation isn't that correct for planar packets, but if the packet was
            // allocated here, there will always only be one buffer.
            int minSize = DrawPacket.computeBufferSize( format, size );
            if( ret.useableBufElemSize( 0 ) >= minSize ) {
                if( format.mType == Jav.AVMEDIA_TYPE_AUDIO ) {
                    ret.nbSamples( size );
                }
                return ret;
            }

            mPool.dispose( ret );
        }

        ret = DrawPacket.create( mPool, format, size );
        mPool.allocated( ret );
        return ret;
    }

    @Override
    protected void freeObject() {
        mPool.close();
    }


    private StreamFormat setPoolFormat( StreamFormat format ) {
        if( format == null || !format.isFullyDefined() ) {
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
