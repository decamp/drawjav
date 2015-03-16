/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.io.*;
import java.util.logging.Logger;

import bits.drawjav.*;
import bits.drawjav.DrawPacket;
import bits.jav.Jav;
import bits.jav.JavException;
import bits.jav.swresample.SwrContext;
import bits.jav.util.*;


/**
 * TODO: Add support for conversion flags.
 *
 * @author decamp
 */
public class AudioResampler implements PacketConverter<DrawPacket> {

    private static final Logger sLog = Logger.getLogger( AudioResampler.class.getName() );

    private final AudioAllocator mAlloc;

    private StreamFormat mSourceFormat        = null;
    private StreamFormat mPredictSourceFormat = null;
    private StreamFormat mRequestedFormat     = null;
    private StreamFormat mDestFormat          = null;

    private int mConversionFlags = 0;

    private boolean mNeedsInit = false;

    // Values that get set on vInit.
    private Stream     mDestStream = null;
    private SwrContext mConverter  = null;
    private Rational   mRateRatio  = null;

    // Get updated each source packet.
    private long       mStreamMicros = Jav.AV_NOPTS_VALUE;
    private AudioTimer mTimer        = new AudioTimer();

    private final long[] mWork = new long[2];

    private boolean mDisposed = false;


    public AudioResampler( AudioAllocator optAlloc ) {
        if( optAlloc == null ) {
            mAlloc = OneFormatAudioAllocator.createPacketLimited( 32, 1024 * 4 );
        } else {
            mAlloc = optAlloc;
            optAlloc.ref();
        }
    }


    public StreamFormat sourceFormat() {
        return mSourceFormat != null ? mSourceFormat : mPredictSourceFormat;
    }


    public void sourceFormat( StreamFormat format ) {
        if( format == mPredictSourceFormat || format != null && format.equals( mPredictSourceFormat ) ) {
            mPredictSourceFormat = format;
            mSourceFormat = null;
            return;
        }

        mPredictSourceFormat = format;
        mSourceFormat = null;
        mNeedsInit = true;

        // Source format may affect destination format if requested format is partially defined.
        updateDestFormat();
    }

    /**
     * @return destination format requested by user. May be partially defined.
     */
    public StreamFormat requestedFormat() {
        return mRequestedFormat;
    }

    /**
     * @param format Requested output format
     */
    public void requestFormat( StreamFormat format ) {
        // Assign format == mRequestedFormat either way.
        // Better to use identical objects than merely equivalent objects.
        if( format == mRequestedFormat || format != null && format.equals( mRequestedFormat ) ) {
            mRequestedFormat = format;
        } else {
            mRequestedFormat = format;
            updateDestFormat();
        }
    }

    /**
     * @return computed destination format. May be different from {@code #requestedFormat()}.
     */
    public StreamFormat destFormat() {
        return mDestFormat;
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


    public DrawPacket convert( DrawPacket source ) throws JavException {
        if( source.isGap() ) {
            source.ref();
            return source;
        }

        if( !StreamFormat.match( mSourceFormat, source )  ) {
            StreamFormat format = StreamFormat.fromAudioPacket( source );
            if( !format.equals( mSourceFormat ) ) {
                mSourceFormat = format;
                mNeedsInit = true;
                updateDestFormat();
            }
        }

        if( mNeedsInit ) {
            init();
        }

        if( mConverter == null ) {
            source.ref();
            return source;
        }

        int srcLen = source.nbSamples();
        int dstLen = (int)Rational.rescale( srcLen, mRateRatio.num(), mRateRatio.den() );

        return doConvert( source, srcLen, dstLen );
    }


    public DrawPacket drain() throws JavException {
        try {
            if( mConverter == null ) {
                return null;
            }
            int dstLen = (int)mConverter.getDelay( mDestFormat.mSampleRate );
            if( dstLen == 0 ) {
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
                DrawPacket p = drain();
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
        StreamFormat source = mSourceFormat != null ? mSourceFormat : mPredictSourceFormat;
        StreamFormat dest = StreamFormat.merge( source, mRequestedFormat );
        if( dest.equals( mDestFormat ) ) {
            return;
        }
        mDestFormat = dest;
        mDestStream = new BasicStream( dest );
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
        StreamFormat src = mSourceFormat;
        StreamFormat dst = mDestFormat;
        if( src == null || dst == null || !src.isFullyDefined() || !dst.isFullyDefined() ) {
            return;
        }

        if( StreamFormat.match( dst, src ) ) {
            return;
        }

        long srcLayout = src.mChannelLayout;
        if( srcLayout == Jav.AV_CH_LAYOUT_NATIVE ) {
            srcLayout = JavChannelLayout.getDefault( src.mChannels );
        }
        long dstLayout = dst.mChannelLayout;
        if( dstLayout == Jav.AV_CH_LAYOUT_NATIVE ) {
            dstLayout = JavChannelLayout.getDefault( dst.mChannels );
        }

        mDestStream = new BasicStream( dst );
        mRateRatio = Rational.reduce( dst.mSampleRate, src.mSampleRate );
        mConverter = SwrContext.allocAndInit( srcLayout,
                                              src.mSampleFormat,
                                              src.mSampleRate,
                                              dstLayout,
                                              dst.mSampleFormat,
                                              dst.mSampleRate );
    }


    private DrawPacket doConvert( DrawPacket src, int srcLen, int dstLen ) throws JavException {
        DrawPacket dst = mAlloc.alloc( mDestFormat, dstLen );
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
                mTimer.init( micros, mDestFormat.mSampleRate );
            }
        }

        mTimer.computeTimestamps( err, mWork );
        // TODO: THis initialization is wrong.
        dst.init( mDestFormat, mWork[0], mWork[1], false );
        dst.stream( null );
        mStreamMicros = mWork[1];

        return dst;
    }

}
