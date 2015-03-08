package bits.drawjav.video;

import bits.drawjav.*;
import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;


/**
* @author Philip DeCamp
*/
public class MultiFormatVideoAllocator extends AbstractRefable implements VideoAllocator {


    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };

    public static MultiFormatVideoAllocator createPacketLimited( int maxPackets ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxPackets, maxPackets * 5, null );
        return new MultiFormatVideoAllocator( pool );
    }

    public static MultiFormatVideoAllocator createByteLimited( long maxBytes ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxBytes, maxBytes * 5, BYTE_COST );
        return new MultiFormatVideoAllocator( pool );
    }



    private final MultiCostPool<DrawPacket> mPool;
    private final AutoPool mAutoPool = new AutoPool();


    MultiFormatVideoAllocator( MultiCostPool<DrawPacket> pool ) {
        mPool = pool;
    }


    @Override
    public synchronized DrawPacket alloc( PictureFormat format ) {
        DrawPacket packet = mPool.poll( format );
        if( packet != null ) {
            return packet;
        }

        if( format != null ) {
            try {
                packet = DrawPacket.createVideo( mPool.pool( format ), format );
            } catch( JavException ex ) {
                throw new RuntimeException( ex );
            }
        } else {
            packet = DrawPacket.createAuto( mAutoPool );
        }

        mPool.allocated( packet );
        return packet;
    }


    @Override
    protected void freeObject() {
        mPool.close();
    }


    private final class AutoPool implements ObjectPool<DrawPacket> {

        @Override
        public DrawPacket poll() {
            return null;
        }

        @Override
        public boolean offer( DrawPacket obj ) {
            return mPool.offer( obj.toPictureFormat(), obj );
        }

    }

}
