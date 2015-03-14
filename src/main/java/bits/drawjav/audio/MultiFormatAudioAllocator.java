package bits.drawjav.audio;

import bits.drawjav.*;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavSampleFormat;
import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;


/**
 * Audio allocator that manages pools for multiple formats.
 *
 * @author Philip DeCamp
 */
public class MultiFormatAudioAllocator extends AbstractRefable implements AudioAllocator {


    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static MultiFormatAudioAllocator createPacketLimited( int maxPackets ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxPackets, maxPackets * 5, null );
        return new MultiFormatAudioAllocator( pool );
    }


    public static MultiFormatAudioAllocator createByteLimited( long maxBytes ) {
        MultiCostPool<DrawPacket> pool = new MultiCostPool<DrawPacket>( maxBytes, maxBytes * 5, BYTE_COST );
        return new MultiFormatAudioAllocator( pool );
    }



    private final MultiCostPool<DrawPacket> mPool;
    private final AutoPool mAutoPool = new AutoPool();


    MultiFormatAudioAllocator( MultiCostPool<DrawPacket> pool ) {
        mPool = pool;
    }


    @Override
    public synchronized DrawPacket alloc( StreamFormat format, int numSamples ) {
        DrawPacket packet = mPool.poll( format );
        if( packet != null ) {
            if( format == null || numSamples < 0 ) {
                return packet;
            }

            int minSize = JavSampleFormat.getBufferSize( format.mChannels,
                                                         numSamples,
                                                         format.mSampleFormat,
                                                         0,
                                                         null );
            if( packet.useableBufElemSize( 0 ) >= minSize ) {
                packet.nbSamples( numSamples );
                return packet;
            }

            mPool.dispose( packet );
        }

        if( format != null ) {
            packet = DrawPacket.createAudio( mPool.pool( format ), format, numSamples, 0 );
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
