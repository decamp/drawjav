package cogmac.peek.bep;

import java.nio.FloatBuffer;

/**
 * Implementation of SOLA-FS. This filter will compress or expand
 * a stream of audio without modifying the pitch. This process can
 * be performed in realtime, and the rate of compression can be
 * adjusted dynamically.
 *
 * @author Philip DeCamp
 */
public class Sola2 {

    private enum State {
        FILL_IN_BUF,
        FILL_OUT_BUF,
        SHIFT_IN_BUF,
        SHIFT_OUT_BUF,
        COMBINE,
        WRITE_OVERLAPPED,
        WRITE_NONOVERLAPPED,
        WAIT_FOR_INPUT_DATA
    }

    private final static float DEFAULT_WINDOW_MILLIS = 20f;
    private final static int SKEW_SAMPLE_STEP        = 8;
    private final static int SKEW_POSITION_STEP      = 2;


    //private final int mFrequency;    //Audio frequency.
    private final float mWinMillis;  //Size of window in milliseconds.
    private final int mWinLength;    //Size of window in samples.
    private final int mWinOverlap;   //Overlapping section of window.
    private final int mWinCopy;      //Portion of overlap section to be copied to output buffer.

    private final int mSkewPos;      //Length of positions over which to check skew.
    private final int mSkewLength;   //Lench of samples over which to compute skew for given position.

    private final float[] mInBuf;
    private int mInIndex;            //Next empty index.

    private final float[] mOutBuf;
    private int mOutIndex;           //Next empty index.

    private final float[] mFade;     //Contains fade coefficients 1.0f -> 0.0f

    private State mState;            //Current state of algorithm.
    private float mRate;             //Current rate of time compression.
    private float mNewRate;          //Holds next rate until it can be synced safely.
    private int mTotalIn;            //Total input samples processed since sync event.
    private int mTotalOut;           //Total output samples processed since sync event.
    private int mNextWinStart;       //Location of next window.


    /**
     * @param frequency  Frequency of the audio. Used to compute appropriate parameters.
     */
    public Sola2( float frequency ) {
        mWinMillis  = DEFAULT_WINDOW_MILLIS;
        mWinLength  = (int)( frequency * 0.001f * mWinMillis + 0.5f );
        mWinOverlap = mWinLength / 2;
        mSkewPos    = mWinLength;
        mSkewLength = mWinOverlap;
        mWinCopy    = Math.min( mWinOverlap, mWinLength - mWinOverlap );

        mInBuf = new float[mWinLength + mSkewPos];
        mInIndex = 0;

        mOutBuf = new float[mWinOverlap];
        mOutIndex = 0;

        mFade = new float[mWinOverlap];
        for( int i = 0; i < mWinOverlap; i++ ) {
            mFade[i] = ( (float)(i + 1) ) / ( mWinOverlap + 2 );
        }

        mState    = State.FILL_IN_BUF;
        mRate     = 1f;
        mNewRate  = Float.NEGATIVE_INFINITY;
        mTotalIn  = 0;
        mTotalOut = 0;
        mNextWinStart = 0;
    }


    /**
     * Processes a series of input samples through SOLA.
     *
     * @param in     Input buffer holding audio samples. 
     *               in.position() will be updated by this call.
     * @param out    Output buffer in which to place output samples. 
     *               out.position() will be updated by this call.
     * @return Number of input samples processed.
     * @throws InterruptedException
     */
    public int process( FloatBuffer in, FloatBuffer out ) {
        int initialCount = in.remaining();

FLOW_LOOP:
        while( in.remaining() > 0 ) {
            switch( mState ) {
            case FILL_IN_BUF:
            {
                int toCopy = Math.min( in.remaining(), mInBuf.length - mInIndex );
                in.get( mInBuf, mInIndex, toCopy );
                mInIndex += toCopy;

                if ( mInIndex != mInBuf.length ) {
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

                mInIndex = skew + mWinOverlap;
                mState = State.WRITE_OVERLAPPED;
                // Fallthrough
            }

            case WRITE_OVERLAPPED:
            {
                // Write overlapped data in mOutBuf to out buffer.
                int available = mWinCopy - mOutIndex;
                int toWrite = Math.min( available, out.remaining() );
                out.put( mOutBuf, mOutIndex, toWrite );
                mOutIndex += toWrite;

                if ( toWrite != available ) {
                    if ( toWrite == 0 ) {
                        break FLOW_LOOP;
                    }
                    break;
                }

                if ( mOutIndex < mWinOverlap ) {
                    mState = State.SHIFT_OUT_BUF;
                    break;
                } else if ( mOutIndex < mWinLength - mWinOverlap ) {
                    mState = State.WRITE_NONOVERLAPPED;
                    break;
                } else {
                    mTotalOut += mOutIndex;
                    mOutIndex = 0;
                    mState = State.FILL_OUT_BUF;
                }
                // Fallthrough
            }

            case FILL_OUT_BUF:
            {
                System.arraycopy( mInBuf, mInIndex, mOutBuf, mOutIndex, mWinOverlap - mOutIndex );
                mOutIndex = 0;

                if( mNewRate > 0f ) {
                    mRate = mNewRate;
                    mNewRate = -1f;
                    mTotalIn = 0;
                    mTotalOut = 0;
                }

                mNextWinStart = (int)(mTotalOut * mRate) - mTotalIn;

                if( mNextWinStart < mInBuf.length ) {
                    mState = State.SHIFT_IN_BUF;

                } else if( mNextWinStart - mInBuf.length < in.remaining() ) {
                    in.position( in.position() + ( mNextWinStart - mInBuf.length ) );
                    mTotalIn += mNextWinStart;
                    mInIndex = 0;
                    mState = State.FILL_IN_BUF;
                    break;

                } else {
                    mTotalIn += ( mInBuf.length + in.remaining() );
                    mNextWinStart -= ( mInBuf.length + in.remaining() );
                    in.position( in.limit() );
                    mInIndex = 0;
                    mState = State.WAIT_FOR_INPUT_DATA;
                    break;
                }

                // Fallthrough
            }

            case SHIFT_IN_BUF:
            {
                System.arraycopy( mInBuf, mNextWinStart, mInBuf, 0, mInBuf.length - mNextWinStart );
                mTotalIn += mNextWinStart;
                mInIndex = mInBuf.length - mNextWinStart;

                if( mInIndex == mInBuf.length ) {
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
                mOutIndex = mWinOverlap - mWinCopy;
                mTotalOut += mWinCopy;

                mState = State.FILL_OUT_BUF;
                break;
            }

            case WRITE_NONOVERLAPPED:
            {
                int available = mWinLength - mWinOverlap - mOutIndex;
                int len = Math.min( available, out.remaining() );
                out.put( mInBuf, mInIndex, len );

                mOutIndex += len;
                mInIndex  += len;  //Not necessary, but useful if performing visualization.

                if( mOutIndex == mWinLength - mWinOverlap ) {
                    mTotalOut += mOutIndex;
                    mOutIndex = 0;
                    mState    = State.FILL_OUT_BUF;
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

        if( mInIndex > 0 ) {
            ret = Math.min( mInIndex, out.remaining() );
            out.put( mInBuf, 0, ret );
        }

        clear();
        return ret;
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


    /**
     * Clears all state in the filter.
     */
    public void clear() {
        mInIndex  = 0;
        mOutIndex = 0;
        mState    = State.FILL_IN_BUF;
        mTotalIn  = 0;
        mTotalOut = 0;
    }


    /**
     * Sets the time compression factor between input and output audio. 
     * For example, calling <code>setRate( 3.5f )</code> will generate an
     * output that is 3.5 times faster than the input. Default is 1.
     * <p>
     * <code>setRate()</code> can be called frequently while filtering to adjust
     * pitch dynamically. There may be some latency between a call to setRate and when
     * that rate takes effect. I'm not sure, but I think the latency will depend
     * on the current overlap window size and might be around 20 milliseconds 
     * for typical use.
     *
     * @param rate Compression factor between input and output. Must be greater than 0.
     */
    public void setRate( float rate ) {
        if ( rate <= 0f ) {
            throw new IllegalArgumentException( "Negative rate." );
        }
        mNewRate = rate;

    }


    private int findSkew() {
        int k = 0;
        double maxCorr = Double.NEGATIVE_INFINITY;
        int maxIndex   = 0;

        for( int i = 0; i < mSkewPos; i += SKEW_POSITION_STEP ) {
            double sum  = 0;
            double norm = 0;

            for ( int j = 0; j < mSkewLength; j += SKEW_SAMPLE_STEP ) {
                sum  += mInBuf[i+j] * mOutBuf[j];
                norm += mInBuf[i+j] * mInBuf[i+j];
            }

            double corr = ( norm == 0 ) ? 0.0 : sum / Math.sqrt(norm);

            if ( corr > maxCorr ) {
                maxCorr = corr;
                maxIndex = i;
            }
        }

        return maxIndex;
    }


}
