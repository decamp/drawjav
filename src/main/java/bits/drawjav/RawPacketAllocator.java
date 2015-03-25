package bits.drawjav;

import bits.jav.codec.JavPacket;
import bits.jav.util.JavBufferPool;
import bits.util.ref.AbstractRefable;

import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class RawPacketAllocator extends AbstractRefable {

    private static final Logger LOG = Logger.getLogger( RawPacketAllocator.class.getName() );

    private static final CostMetric<JavPacket> BYTE_COST = new CostMetric<JavPacket>() {
        @Override
        public long costOf( JavPacket p ) {
            return 64 + p.bufSize();
        }
    };


    public static RawPacketAllocator createPacketLimited( int maxPackets ) {
        CostPool<JavPacket> pool = new CostPool<JavPacket>( maxPackets, maxPackets * 100, null );
        return new RawPacketAllocator( pool );
    }


    public static RawPacketAllocator createByteLimited( long maxBytes ) {
        CostPool<JavPacket> pool = new CostPool<JavPacket>( maxBytes, maxBytes * 100, BYTE_COST );
        return new RawPacketAllocator( pool );
    }


    private final CostPool<JavPacket> mPool;
    private JavBufferPool mJavPool = null;
    private int mAllocSize = -1;


    RawPacketAllocator( CostPool<JavPacket> pool ) {
        mPool = pool;
    }


    public synchronized JavPacket alloc( StreamFormat format, int size ) {
        if( size > mAllocSize ) {
            if( mJavPool != null ) {
                mJavPool.deref();
                mJavPool = null;
            }
            mAllocSize = size;
            mJavPool = JavBufferPool.init( mAllocSize );
        }

        JavPacket ret = mPool.poll();
        if( ret != null ) {
            ret.makeWritable( size, mJavPool );
            return ret;
        }
        return JavPacket.alloc( mPool );
    }

    @Override
    protected void freeObject() {
        mPool.close();
    }

}
