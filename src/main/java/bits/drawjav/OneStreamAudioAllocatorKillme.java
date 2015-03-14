/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.drawjav.audio.AudioAllocator;
import bits.jav.Jav;
import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavSampleFormat;
import bits.util.ref.AbstractRefable;
import bits.util.ref.ObjectPool;

import java.nio.ByteBuffer;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class OneStreamAudioAllocatorKillme extends AbstractRefable implements AudioAllocator {

    private static final Logger LOG = Logger.getLogger( OneStreamAudioAllocatorKillme.class.getName() );

    private static final CostMetric<JavFrame> BYTE_COST = new CostMetric<JavFrame>() {
        @Override
        public long costOf( JavFrame p ) {
            return 512 + p.useableBufElemSize( 0 );
        }
    };


    public static OneStreamAudioAllocatorKillme createPacketLimited( int maxPackets, int defaultSampleNum ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxPackets, maxPackets * 100, null );
        return new OneStreamAudioAllocatorKillme( pool, defaultSampleNum );
    }


    public static OneStreamAudioAllocatorKillme createByteLimited( long maxBytes, int defaultSampleNum ) {
        CostPool<DrawPacket> pool = new CostPool<DrawPacket>( maxBytes, maxBytes * 100, BYTE_COST );
        return new OneStreamAudioAllocatorKillme( pool, defaultSampleNum );
    }


    private final CostPool<DrawPacket> mPool;

    private AudioFormat mPoolFormat;
    private int         mDefaultSampleNum;

    private boolean mHasFormat        = false;
    private boolean mHasChangedFormat = false;


    OneStreamAudioAllocatorKillme( CostPool<DrawPacket> pool, int defaultSampleNum ) {
        mPool = pool;
        mDefaultSampleNum = defaultSampleNum;
    }


    public synchronized DrawPacket alloc( AudioFormat format ) {
        return alloc( format, mDefaultSampleNum );
    }

    @Override
    public synchronized DrawPacket alloc( AudioFormat format, int numSamples ) {
        if( !checkFormat( format, mPoolFormat ) ) {
            format = setPoolFormat( format );
        }

        mHasFormat = true;
        DrawPacket ret = mPool.poll();

        if( ret != null ) {
            if( format == null || numSamples < 0 ) {
                return ret;
            }

            // Check if packet can hold requested number of samples.
            // This calculation isn't that correct for planar packets, but if the packet was
            // allocated here, there will always only be one buffer.
            int minSize = JavSampleFormat.getBufferSize( format.mChannels,
                                                         numSamples,
                                                         format.mSampleFormat,
                                                         0,
                                                         null );
            if( ret.useableBufElemSize( 0 ) >= minSize ) {
                ret.nbSamples( numSamples );
                return ret;
            }

            mPool.dispose( ret );
        }

        if( format != null && numSamples > 0 ) {
            ret = DrawPacketTest.createAudio( mPool, format, numSamples, 0 );
        } else {
            ret = DrawPacketTest.createAuto( mPool );
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


    private static class DrawPacketTest extends DrawPacket {

        public static DrawPacketTest createAuto( ObjectPool<? super DrawPacket> optPool ) {
            long p = nAllocFrame();
            if( p == 0 ) {
                throw new OutOfMemoryError( "Allocation failed." );
            }
            return new DrawPacketTest( p, optPool );
        }

        public static DrawPacket createAudio( ObjectPool<? super DrawPacket> optPool,
                                              AudioFormat format,
                                              int samplesPerChannel,
                                              int align )
        {
            assert samplesPerChannel >= 0;

            int size = JavSampleFormat.getBufferSize( format.mChannels,
                                                      samplesPerChannel,
                                                      format.mSampleFormat,
                                                      align,
                                                      null );
            if( size < 0 ) {
                throw new RuntimeException( new JavException( size ) );
            }

            ByteBuffer buf = Jav.allocBuffer( size );
            return createAudio( optPool, format, samplesPerChannel, align, buf );
        }


        public static DrawPacketTest createAudio( ObjectPool<? super DrawPacket> optPool,
                                                  AudioFormat format,
                                                  int samplesPerChannel,
                                                  int align,
                                                  ByteBuffer buf )
        {
            long pointer = nAllocFrame();
            if( pointer == 0 ) {
                throw new OutOfMemoryError();
            }

            DrawPacketTest ret = new DrawPacketTest( pointer, optPool );
            ret.fillAudioFrame( format.mChannels,
                                samplesPerChannel,
                                format.mSampleFormat,
                                buf,
                                align );
            return ret;
        }


        @SuppressWarnings( { "unchecked", "rawtypes" } )
        protected DrawPacketTest( long pointer, ObjectPool<? super DrawPacket> pool ) {
            super( pointer, (ObjectPool)pool );
        }


        @Override
        public boolean ref() {
            return super.ref();
        }

        @Override
        public void deref() {
            super.deref();
        }

    }

}

