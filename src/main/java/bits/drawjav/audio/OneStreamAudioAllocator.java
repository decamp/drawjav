/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.util.logging.Logger;

import bits.drawjav.CostMetric;
import bits.drawjav.CostPool;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavSampleFormat;
import bits.util.ref.AbstractRefable;


/**
 * @author Philip DeCamp
 */
public class OneStreamAudioAllocator extends AbstractRefable implements AudioAllocator {

    private static final Logger LOG = Logger.getLogger( OneStreamAudioAllocator.class.getName() );

    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static OneStreamAudioAllocator createPacketLimited( int maxPackets, int defaultSampleNum ) {
        CostPool<AudioPacket> pool = new CostPool<AudioPacket>( maxPackets, maxPackets * 100, null );
        return new OneStreamAudioAllocator( pool, defaultSampleNum );
    }


    public static OneStreamAudioAllocator createByteLimited( long maxBytes, int defaultSampleNum ) {
        CostPool<AudioPacket> pool = new CostPool<AudioPacket>( maxBytes, maxBytes * 100, BYTE_COST );
        return new OneStreamAudioAllocator( pool, defaultSampleNum );
    }



    private final CostPool<AudioPacket> mPool;

    private AudioFormat mPoolFormat;
    private int mDefaultSampleNum;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;


    OneStreamAudioAllocator( CostPool<AudioPacket> pool, int defaultSampleNum ) {
        mPool = pool;
        mDefaultSampleNum = defaultSampleNum;
    }


    public synchronized AudioPacket alloc( AudioFormat format ) {
        return alloc( format, mDefaultSampleNum );
    }

    @Override
    public synchronized AudioPacket alloc( AudioFormat format, int numSamples ) {
        if( !checkFormat( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        AudioPacket ret = mPool.poll();

        if( ret != null ) {
            if( format == null || numSamples < 0 ) {
                return ret;
            }

            // Check if packet can hold requested number of samples.
            // This calculation isn't that correct for planar packets, but if the packet was
            // allocated here, there will always only be one buffer.
            int minSize = JavSampleFormat.getBufferSize( format.channels(), numSamples, format.sampleFormat(), 0, null );
            if( ret.useableBufElemSize( 0 ) >= minSize ) {
                ret.nbSamples( numSamples );
                return ret;
            }

            mPool.dispose( ret );
        }

        if( format != null && numSamples > 0 ) {
            ret = AudioPacket.createFilled( mPool, format, numSamples, 0 );
        } else {
            ret = AudioPacket.createAuto( mPool );
        }

        mPool.allocated( ret );
        return ret;
    }

    @Override
    protected void freeObject() {
        mPool.close();
    }


    private boolean checkFormat( AudioFormat a, AudioFormat b ) {
        return a == b || a != null && a.equals( b );
    }


    private AudioFormat setPoolFormat( AudioFormat format ) {
        if( !AudioFormat.isFullyDefined( format ) ) {
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

