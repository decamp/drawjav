package bits.drawjav;

import bits.jav.Jav;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavBufferRef;
import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;


/**
 * Audio allocator that manages pools for multiple formats.
 *
 * @author Philip DeCamp
 */
public class MultiFormatAllocator extends AbstractRefable implements PacketAllocator<DrawPacket> {


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

// TODO: Fix memory allocation. Because it's nearly impossible to prevent FFMPEG from reallocating buffers
// it's really hard to manage memory that way.
//    public static MultiFormatAllocator createByteLimited( long maxBytes ) {
//        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxBytes, maxBytes * 5, BYTE_COST );
//        return new MultiFormatAllocator( pool );
//    }



    private final MultiCostPool<DrawPacket> mPool;
    private final AutoPool mAutoPool = new AutoPool();


    MultiFormatAllocator( MultiCostPool<DrawPacket> pool ) {
        mPool = pool;
    }


    @Override
    public synchronized DrawPacket alloc( StreamFormat format, int size ) {
        DrawPacket ret = mPool.poll( format );
        if( ret != null ) {
            // TODO: Figure out what the ffmpeg is doing with data buffer pointers.
            if( format == null || size <= 0 ) {
                return ret;
            }

            int minSize = Jav.encodingBufferSize( DrawPacket.computeBufferSize( format, size ) );
            if( ret.useableBufElemSize( 0 ) >= minSize ) {
                if( format.mType == Jav.AVMEDIA_TYPE_AUDIO ) {
                    ret.nbSamples( size );
                }
                return ret;
            }
            mPool.dispose( ret );
        }

        ret = DrawPacket.create( mPool.pool( format ), format, size );
        mPool.allocated( ret );
        return ret;
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


    public static String formatBufData( DrawPacket packet ) {
        StringBuilder s = new StringBuilder();
        s.append( packet.dataElem( 0 ) );

        JavBufferRef ref = packet.bufElem( 0 );
        if( ref == null ) {
            s.append( "  <null>" );
        } else {
            s.append( "  " + ref.data() + "  " + ref.size() );
            ref.unref();
        }

        return s.toString();
    }

}
