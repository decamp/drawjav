/*
 * Copyright (c) 2015. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */
package bits.drawjav.audio;

import bits.microtime.Frac;

import java.nio.FloatBuffer;


/** 
 * Implementation of SOLA-FS. This filter will compress or expand
 * a stream of audio without modifying the pitch. This process can
 * be performed in realtime, and the rate of compression can be
 * adjusted dynamically.
 * 
 * @author Philip DeCamp
 */
public class SolaFilter {
    
    private enum State{
        FILL_IN_BUF,
        FILL_OUT_BUF,
        SHIFT_IN_BUF,
        SHIFT_OUT_BUF,
        COMBINE,
        WRITE_OVERLAPPED,
        WRITE_NONOVERLAPPED,
        WAIT_FOR_INPUT_DATA
    }

    private final static long  DEFAULT_WINDOW_MICROS = 20000L;
    private final static int   SKEW_SAMPLE_STEP      = 8;
    private final static int   SKEW_POSITION_STEP    = 2;


    private final long  mWinMicros;  //Size of window in microseconds.
    private final int   mWinLength;  //Size of window in samples.
    private final int   mWinOverlap; //Overlapping section of window.
    private final int   mWinCopy;    //Portion of overlap section to be copied to output buffer.

    private final int mSkewCheckNum; // Number of positions to check when searching for optimal skew.
    private final int mSkewCheckLen; // Number of samples to process when computing skew for given position.

    private final float[] mInBuf;
    private       int     mInPos;    //Next empty index.

    private final float[] mOutBuf;
    private       int     mOutPos;   //Next empty index.

    private State mState;            // Current mode of algorithm.
    private Frac  mRate;             // Current rate of time compression.
    private Frac  mNewRate;          // Holds next rate until it can be synced safely.
    private int   mTotalIn;          // Total input samples processed since sync event.
    private int   mTotalOut;         // Total output samples processed since sync event.
    private int   mNextWinStart;     // Location of next window.

    private final float[] mFade;     // Contains cross-fade coefficients 1.0f -> 0.0f


    /**
     * @param frequency  Frequency of the audio. Used to compute appropriate parameters.
     */
    public SolaFilter( int frequency ) {
        mWinMicros    = DEFAULT_WINDOW_MICROS;
        mWinLength    = (int)Frac.multLong( mWinMicros, frequency, 1000000 );
        mWinOverlap   = mWinLength / 2;
        mSkewCheckNum = mWinLength;
        mSkewCheckLen = mWinOverlap;
        mWinCopy = Math.min( mWinOverlap, mWinLength - mWinOverlap );

        mInBuf = new float[mWinLength + mSkewCheckNum];
        mInPos = 0;

        mOutBuf = new float[mWinOverlap];
        mOutPos = 0;

        mFade = new float[mWinOverlap];
        for( int i = 0; i < mWinOverlap; i++ ) {
            mFade[i] = ((float)(i + 1)) / (mWinOverlap + 2);
        }

        mState        = State.FILL_IN_BUF;
        mRate         = new Frac( 1, 1 );
        mNewRate      = null;
        mTotalIn      = 0;
        mTotalOut     = 0;
        mNextWinStart = 0;
    }


    /**
     * Sets the time compression factor between input and output audio.
     * For example, calling <code>rate( 3.5f )</code> will generate an
     * output that is 3.5 times faster than the input. Default is 1.
     * <p>
     * <code>rate()</code> can be called frequently while filtering to adjust
     * pitch dynamically. There may be some latency between a call to rate and when
     * that rate takes effect. I'm not sure, but I think the latency will depend
     * on the current overlap window size and might be around 20 milliseconds
     * for typical use.
     *
     * @param rate Compression factor between input and output. Must be greater than 0.
     */
    public void rate( Frac rate ) {
        mNewRate = new Frac( rate );
    }

    /**
     * @return current compression factor between input and output audio.
     * @see #rate( Frac )
     */
    public Frac rate() {
        return new Frac( mNewRate != null ? mNewRate : mRate );
    }

    /**
     * Processes a series of input samples through SOLA.
     * 
     * @param in   Input buffer holding audio samples.
     *             {@code in.position()} will be updated by this call.
     * @param out  Output buffer in which to place output samples.
     *             {@code out.position()} will be updated by this call.
     * @return Number of <b>input</b> samples processed.
     */
    public int process( FloatBuffer in, FloatBuffer out ) {
        int initialCount = in.remaining();
        
FLOW_LOOP:
        while( in.remaining() > 0 ) {
            switch( mState ) {
            case FILL_IN_BUF:
            {
                int toCopy = Math.min( in.remaining(), mInBuf.length - mInPos );
                in.get( mInBuf, mInPos, toCopy );
                mInPos += toCopy;
                if ( mInPos != mInBuf.length ) {
                    // Not filled yet.
                    break;
                }
                mState = State.COMBINE;
                // Fallthrough
            }
            
            case COMBINE:
            {
                // Combine input into output.
                int skew = findSkew();
                for( int i = 0; i < mWinOverlap; i++ ) {
                    mOutBuf[i] = mOutBuf[i] * (1f - mFade[i]) + mInBuf[i+skew] * mFade[i];
                }
                mInPos = skew + mWinOverlap;
                mState = State.WRITE_OVERLAPPED;
                // Fallthrough
            }
            
            case WRITE_OVERLAPPED:
            {
                // Write overlapped data in mOutBuf to out buffer.
                int available = mWinCopy - mOutPos;
                int toWrite = Math.min( available, out.remaining() );
                out.put( mOutBuf, mOutPos, toWrite );
                mOutPos += toWrite;
                
                if ( toWrite != available ) {
                    if ( toWrite == 0 ) {
                        break FLOW_LOOP;
                    }                    
                    break;
                }
                
                if ( mOutPos < mWinOverlap ) {
                    mState = State.SHIFT_OUT_BUF;
                    break;
                } else if ( mOutPos < mWinLength - mWinOverlap ) {
                    mState = State.WRITE_NONOVERLAPPED;
                    break;
                } else {
                    mTotalOut += mOutPos;
                    mOutPos = 0;
                    mState = State.FILL_OUT_BUF;
                }
                // Fallthrough
            }
            
            case FILL_OUT_BUF:
            {
                System.arraycopy( mInBuf, mInPos, mOutBuf, mOutPos, mWinOverlap - mOutPos );
                mOutPos = 0;
                if( mNewRate != null ) {
                    mRate     = mNewRate;
                    mNewRate  = null;
                    mTotalIn  = 0;
                    mTotalOut = 0;
                }
                
                mNextWinStart = (int)( Frac.multLong( mTotalOut, mRate.mNum, mRate.mDen ) - mTotalIn );
                
                if( mNextWinStart < mInBuf.length ) {
                    mState = State.SHIFT_IN_BUF;
                    
                } else if( mNextWinStart - mInBuf.length < in.remaining() ) {
                    in.position( in.position() + ( mNextWinStart - mInBuf.length ) );
                    mTotalIn += mNextWinStart;
                    mInPos = 0;
                    mState = State.FILL_IN_BUF;
                    break;
                    
                } else {
                    mTotalIn += ( mInBuf.length + in.remaining() );
                    mNextWinStart -= ( mInBuf.length + in.remaining() );
                    in.position( in.limit() );
                    mInPos = 0;
                    mState = State.WAIT_FOR_INPUT_DATA;
                    break;
                }
                
                // Fallthrough
            }
            
            case SHIFT_IN_BUF:
            {
                System.arraycopy( mInBuf, mNextWinStart, mInBuf, 0, mInBuf.length - mNextWinStart );
                mTotalIn += mNextWinStart;
                mInPos = mInBuf.length - mNextWinStart;
                if( mInPos == mInBuf.length ) {
                    mState = State.COMBINE;
                } else {
                    mState = State.FILL_IN_BUF;
                }
                break;
            }
            
            case WAIT_FOR_INPUT_DATA:
            {
                if( mNextWinStart < in.remaining() ) {
                    in.position( in.position() + mNextWinStart );
                    mTotalIn += mNextWinStart;
                    mState = State.FILL_IN_BUF;
                } else {
                    mTotalIn += in.remaining();
                    mNextWinStart -= in.remaining();
                    in.position( in.limit() );
                }
                
                break;
            }
            
            case SHIFT_OUT_BUF:
            {
                System.arraycopy( mOutBuf, mWinCopy, mOutBuf, 0, mWinOverlap - mWinCopy );
                mOutPos = mWinOverlap - mWinCopy;
                mTotalOut += mWinCopy;
                
                mState = State.FILL_OUT_BUF;
                break;
            }
            
            case WRITE_NONOVERLAPPED:
            {
                int available = mWinLength - mWinOverlap - mOutPos;
                int len = Math.min( available, out.remaining() );
                out.put( mInBuf, mInPos, len );
                
                mOutPos += len;
                mInPos += len;  //Not necessary, but useful if performing visualization.
                
                if( mOutPos == mWinLength - mWinOverlap ) {
                    mTotalOut += mOutPos;
                    mOutPos = 0;
                    mState = State.FILL_OUT_BUF;
                }
                if( len != available ) {
                    break FLOW_LOOP;
                }
                
                break;
            }}
        }
        
        return initialCount - in.remaining();
    }

    /**
     * Flushes all remaining audio samples held internally. Should be called
     * at end of processing.
     *  
     * @param out Output buffer with at least <code>minFlushBufferSize()</code> values remaining.
     *            out.position() will be updated by this call.
     * @return Number of samples written to output.
     */
    public int flush( FloatBuffer out ) {
        int ret = 0;
        if( mInPos > 0 ) {
            ret = Math.min( mInPos, out.remaining() );
            out.put( mInBuf, 0, ret );
        }
        clear();
        return ret;
    }

    /**
     * Clears all state in the filter.
     */
    public void clear() {
        mInPos    = 0;
        mOutPos   = 0;
        mState    = State.FILL_IN_BUF;
        mTotalIn  = 0;
        mTotalOut = 0;
    }

    /**
     * Returns the minimum size of a float buffer needed for a complete
     * flush. For users of the <code>flushAudio()</code> method, this
     * method should be called to determine the minimum size of an output
     * buffer.
     * 
     * @return Minimum size for a buffer to guarantee complete flush.
     */
    public int minFlushBufferSize() {
        return mInBuf.length;
    }


    private int findSkew() {
        int k = 0;
        double maxCorr = Double.NEGATIVE_INFINITY;
        int maxIndex = 0;
        
        for( int i = 0; i < mSkewCheckNum; i += SKEW_POSITION_STEP ) {
            double sum  = 0;
            double norm = 0;
            
            for( int j = 0; j < mSkewCheckLen; j += SKEW_SAMPLE_STEP ) {
                sum  += mInBuf[i+j] * mOutBuf[j];
                norm += mInBuf[i+j] * mInBuf[i+j];
            }
            
            double corr = ( norm == 0 ) ? 0.0 : sum / Math.sqrt( norm );
            if( corr > maxCorr ) {
                maxCorr = corr;
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }

}
