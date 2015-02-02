/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.io.*;
import java.util.logging.Logger;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.jav.JavException;
import bits.jav.swresample.SwrContext;
import bits.jav.util.*;


/**
 * TODO: Add support for conversion flags.
 *
 * @author decamp
 */
public class AudioPacketResampler implements PacketConverter<AudioPacket> {

    private static final Logger sLog = Logger.getLogger( AudioPacketResampler.class.getName() );

    private final AudioAllocator mAlloc;

    private AudioFormat mSrcFormat       = null;
    private AudioFormat mRequestedFormat = null;
    private AudioFormat mDstFormat       = null;
    private int         mConversionFlags = 0;

    private boolean mNeedsInit = false;

    // Values that get set on init.
    private SwrContext mConverter = null;
    private Rational   mRateRatio = null;

    // Get updated each source packet.
    private StreamHandle mStream       = null;
    private long         mStreamMicros = Jav.AV_NOPTS_VALUE;
    private AudioTimer   mTimer        = new AudioTimer();

    private final long[] mWork = new long[2];

    private boolean mDisposed = false;


    public AudioPacketResampler( AudioAllocator optAlloc ) {
        if( optAlloc == null ) {
            mAlloc = new OneStreamAudioAllocator( 8, -1, 1024 * 4 );
        } else {
            mAlloc = optAlloc;
            optAlloc.ref();
        }
    }


    public AudioFormat sourceFormat() {
        return mSrcFormat;
    }


    public void sourceFormat( AudioFormat format ) {
        if( format == mSrcFormat || format != null && format.equals( mSrcFormat ) ) {
            mSrcFormat = format;
            return;
        }

        mSrcFormat = format;
        mNeedsInit = true;

        // Source format may affect destination format if requested format is partially defined.
        updateDestFormat();
    }

    /**
     * @return destination format requested by user. May be partially defined.
     */
    public AudioFormat requestedFormat() {
        return mRequestedFormat;
    }

    /**
     * @return computed destination format. May be different from {@code #requestedFormat()}.
     */
    public AudioFormat destFormat() {
        return mDstFormat;
    }

    /**
     * @param format Requested output format
     */
    public void destFormat( AudioFormat format ) {
        // Assign format == mRequestedFormat either way.
        // Better to use identical objects than merely equivalent objects.
        if( format == mDstFormat || format != null && format.equals( mDstFormat ) ) {
            mRequestedFormat = format;
        } else {
            mRequestedFormat = format;
            updateDestFormat();
        }
    }


    public int conversionFlags() {
        return mConversionFlags;
    }


    public void conversionFlags( int flags ) {
        if( flags == mConversionFlags ) {
            return;
        }
        mConversionFlags = flags;
        mNeedsInit = true;
    }


    public AudioPacket convert( AudioPacket src ) throws JavException {
        AudioFormat fmt = src.audioFormat();
        if( fmt != mSrcFormat ) {
            sourceFormat( fmt );
        }

        if( mNeedsInit ) {
            init();
        }

        if( mConverter == null ) {
            src.ref();
            return src;
        }

        int srcLen = src.nbSamples();
        int dstLen = (int)Rational.rescale( srcLen, mRateRatio.num(), mRateRatio.den() );
        mStream = src.stream();

        return doConvert( src, srcLen, dstLen );
    }


    public AudioPacket drain() throws JavException {
        try {
            if( mConverter == null ) {
                return null;
            }
            int dstLen = (int)mConverter.getDelay( mDstFormat.sampleRate() );
            if( dstLen != 0 ) {
                return null;
            }
            return doConvert( null, 0, dstLen );
        } finally {
            mStreamMicros = Jav.AV_NOPTS_VALUE;
        }
    }
    

    public void clear() {
        if( mConverter != null ) {
            try {
                AudioPacket p = drain();
                if( p != null ) {
                    p.deref();
                }
            } catch( IOException ex ) {
                sLog.warning( "Error occurred during AudioPacketResampler.clear(). Releasing SwrContext." );
                invalidateConverter();
            }
        }

        mStreamMicros = Jav.AV_NOPTS_VALUE;
    }
    
    
    public void close() {
        if( mDisposed ) {
            return;
        }
        mDisposed = true;
        mAlloc.deref();
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }
    }


    private void updateDestFormat() {
        AudioFormat format = AudioFormat.merge( mSrcFormat, mRequestedFormat );
        if( !AudioFormat.isFullyDefined( format ) && format.equals( mDstFormat ) ) {
            return;
        }
        mDstFormat = format;
        invalidateConverter();
    }


    private void invalidateConverter() {
        if( mNeedsInit ) {
            return;
        }
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }
        mNeedsInit = true;
    }


    private void init() throws JavException {
        mNeedsInit = false;
        AudioFormat src = mSrcFormat;
        AudioFormat dst = mDstFormat;
        if( !AudioFormat.isFullyDefined( src ) || !AudioFormat.isFullyDefined( dst ) ) {
            return;
        }

        if( dst.equals( src ) ) {
            return;
        }

        long srcLayout = src.channelLayout();
        if( srcLayout == Jav.AV_CH_LAYOUT_NATIVE ) {
            srcLayout = JavChannelLayout.getDefault( src.channels() );
        }
        long dstLayout = dst.channelLayout();
        if( dstLayout == Jav.AV_CH_LAYOUT_NATIVE ) {
            dstLayout = JavChannelLayout.getDefault( dst.channels() );
        }

        mRateRatio = Rational.reduce( dst.sampleRate(), src.sampleRate() );
        mConverter = SwrContext.allocAndInit( srcLayout,
                                              src.sampleFormat(),
                                              src.sampleRate(),
                                              dstLayout,
                                              dst.sampleFormat(),
                                              dst.sampleRate() );
    }


    private AudioPacket doConvert( AudioPacket src, int srcLen, int dstLen ) throws JavException {
        AudioPacket dst = mAlloc.alloc( mDstFormat, dstLen );
        //System.out.format( "0x%X   0x%X    %d\n", src.data(), src.extendedData(), src.channels() );
        //System.out.format( "  0x%X  0x%X \n", src.dataElem( 0 ), src.dataElem( 1 ) );
        //System.out.format( "  0x%X  0x%X \n", src.extendedDataElem( 0 ), src.extendedDataElem( 1 ) );
        //System.out.format( "  %s  %s \n", src.bufElem( 0 ), src.bufElem( 1 ) );
        //System.out.format( "  0x%X   0x%X\n", dst.data(), dst.extendedData() );
        int err = mConverter.convert( src, srcLen, dst, dstLen );
        if( err <= 0 ) {
            dst.deref();
            if( err < 0 ) {
                throw new JavException( err );
            } else {
                return null;
            }
        }

        if( src != null ) {
            long micros = src.startMicros();
            if( micros != mStreamMicros ) {
                mTimer.init( micros, mDstFormat.sampleRate() );
            }
        }

        mTimer.computeTimestamps( err, mWork );
        dst.init( mStream, mDstFormat, mWork[0], mWork[1] );
        mStreamMicros = mWork[1];

        return dst;
    }

}
