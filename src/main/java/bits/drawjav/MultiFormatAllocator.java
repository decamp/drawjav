package bits.drawjav;

import bits.jav.Jav;
import bits.jav.codec.JavFrame;
import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;


/**
 * Audio allocator that manages pools for multiple formats.
 *
 * @author Philip DeCamp
 */
public class MultiFormatAllocator extends AbstractRefable implements PacketAllocator {


    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static MultiFormatAllocator createPacketLimited( int maxPackets ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxPackets, maxPackets * 5, null );
        return new MultiFormatAllocator( pool );
    }


    public static MultiFormatAllocator createByteLimited( long maxBytes ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxBytes, maxBytes * 5, BYTE_COST );
        return new MultiFormatAllocator( pool );
    }



    private final MultiCostPool<DrawPacket> mPool;
    private final AutoPool mAutoPool = new AutoPool();


    MultiFormatAllocator( MultiCostPool<DrawPacket> pool ) {
        mPool = pool;
    }


    @Override
    public synchronized DrawPacket alloc( StreamFormat format, int size ) {
        DrawPacket packet = mPool.poll( format );
        if( packet != null ) {
            if( format == null || size < 0 ) {
                return packet;
            }

            int minSize = DrawPacket.computeBufferSize( format, size );
            if( packet.useableBufElemSize( 0 ) >= minSize ) {
                if( format.mType == Jav.AVMEDIA_TYPE_AUDIO ) {
                    packet.nbSamples( size );
                }
                return packet;
            }
            mPool.dispose( packet );
        }

        packet = DrawPacket.create( mPool.pool( format ), format, size );
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
            return mPool.offer( StreamFormat.fromAudioPacket( obj ), obj );
        }

    }

}
