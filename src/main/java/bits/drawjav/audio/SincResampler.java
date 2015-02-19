package bits.drawjav.audio;

import bits.jav.util.Rational;


/**
* Based on audio resampler used in Vorbis Tools. <br/>
* Supports arbitrary sample-rate conversion. <br/>
* Uses kaiser-windowed sinc filter.
*
* This file is kept around because it's nice to have a pure java tool around.
* However, it's recommended you use swsresample instead.
*/
public class SincResampler {

    private final int     mChannels;
    private final int     mInFreq;
    private final int     mOutFreq;
    private final int     mTaps;
    private final float[] mTable;
    private final float[] mTail;
    private final Pool[]  mPools;

    /**
     * @param inFreq        Input frequency of resampler. Must be greater than zero.
     * @param outFreq       Output frequency of resampler. Must be greater than zero.
     * @param numChannels   Number of channels of audio signal. All audio sample arres are interleaved. Must be greater than zero.
     **/
    public SincResampler( int inFreq, int outFreq, int numChannels ) {
        this( inFreq, outFreq, numChannels, 1.0, 0.80, 45, 16.0 );
    }

    /**
     * @param inFreq       Input frequency of resampler. Must be greater than zero.
     * @param outFreq      Output frequency of resampler. Must be greater than zero.
     * @param numChannels  Number of channels of audio signal. All audio sample arres are interleaved. Must be greater than zero.
     * @param gain         Amount by which to amplify audio signal.  Gain is multiplier, not decibels. Default is 1.0.
     * @param cutoff       Not entirely sure. {@code  0.01 < cutoff <= 1.0 }  Default is 0.80.
     * @param taps         Not entirely sure. {@code  2 < taps <= 1000 } Default is 45.
     * @param beta         Not entirely sure. {@code  beta > 16.0 } Default is 16.0.
     */
    public SincResampler( int inFreq,
                          int outFreq,
                          int numChannels,
                          double gain,
                          double cutoff,
                          int taps,
                          double beta )
    {
        assert numChannels > 0;
        assert outFreq > 0;
        assert inFreq > 0;
        assert cutoff >= 0.01 && cutoff <= 1.0;
        assert taps > 2 && taps <= 1000;
        assert beta > 2.0;

        int factor = Rational.gcd( inFreq, outFreq );
        mChannels  = numChannels;
        mInFreq    = inFreq / factor;
        mOutFreq   = outFreq / factor;
        mTaps      = taps;
        mTable     = new float[mOutFreq * mTaps];
        mTail      = new float[mTaps];

        if( mOutFreq < mInFreq ) {
            // push the cutoff frequency down to the output frequency
            cutoff = cutoff * mOutFreq / mInFreq;

            // compensate for the sharper roll-off requirement
            // (this method I found empirically, and don't understand, but it's fast)
            beta = beta * mOutFreq * mOutFreq / ( mInFreq * mInFreq );
        }

        filtSinc( mTable, mOutFreq, cutoff, gain, mTaps );
        winKaiser( mTable, beta, mTaps );



        mPools = new Pool[numChannels];
        for( int i = 0; i < numChannels; i++ ) {
            mPools[i] = new Pool(mTaps);
        }
    }



    /**
     * The input frequency of the resampler.
     * This may not be the same as provided to the constructor,
     * but the ratio between input and output will be the same.
     *
     * @return input frequency of resampler
     */
    public int inputFrequency() {
        return mInFreq;
    }

    /**
     * The output frequency of the resampler.
     * This may not be the same as provided to the constructor,
     * but the ratio between the input and output frequencies will be the same.
     *
     * @return output frequency of resampler
     */
    public int outputFrequency() {
        return mOutFreq;
    }

    /**
     * @return Number of channels of audio signal.
     */
    public int channels() {
        return mChannels;
    }

    /**
     * @param inFrames  Number of possible input frames.
     * @return the recommended size of output buffer to use for receiving output.
     */
    public int recommendOutBufferSize( int inFrames ) {
        //The 16 * mChannels provides a bit of leeway.
        return ( inFrames * mOutFreq / mInFreq + 16 ) * mChannels;
    }

    /**
     * @return the recommended size of output buffer to use for draining samples.
     */
    public int recommendDrainBufferSize() {
        return recommendOutBufferSize( mChannels * (mTaps / 2) );
    }

    /**
     * @param outFrames Size of an output buffer in frames.
     * @return the maximum number of input samples that can be processed and stored in a buffer of size "outFrames".
     */
    public int recommendInBufferSize( int outFrames ) {
        int n = ( outFrames - 16 * mChannels ) * mInFreq / mOutFreq;
        return Math.max( 0, n );
    }

    /**
     * Resamples some number of input frames.
     *
     * @param in      Input array containing samples (interleaved if multiple channels).
     * @param inOff   Offset into input array
     * @param out     Output buffer
     * @param outOff  Offset into output array
     * @param len     Number of input FRAMES to convert, or SAMPLES PER CHANNEL. NOT number of array VALUES.
     *
     * @return the number of output frames generated (interleaved if multiple channels).
     */
    public int process( float[] in, int inOff, float[] out, int outOff, int len ) {
        int ret = 0;

        for( int i = 0; i < mChannels; i++ ) {
            ret = push( mPools[i],
                        in,
                        inOff + i,
                        mChannels,
                        out,
                        outOff + i,
                        mChannels,
                        len );
        }

        return ret;
    }

    /**
     * Drains all buffered data into an output buffer. Multichannel data will be interleaved.
     *
     * @param out     Output buffer.
     * @param outOff  Offset into output buffer.
     * @return the number of samples placed into the output buffer.
     */
    public int drain( float[] out, int outOff ) {
        int ret = 0;
        for( int i = 0; i < mChannels; i++ ) {
            ret += push( mPools[i], mTail, 0, 1, out, outOff + i, mChannels, mTaps / 2 - 1 );
            mPools[i].reset();
        }

        return ret;
    }

    /**
     * Clears state.
     */
    public void clear() {
        for(int i = 0; i < mPools.length; i++) {
            mPools[i].reset();
        }
    }



    private int push( Pool pool,
                      float[] src,
                      int srcOff,
                      int srcStep,
                      float[] dst,
                      int dstOff,
                      int dstStep,
                      int srcLen )
    {
//      assert(pool->mFill);
//      assert(dest);
//      assert(source);
//      assert(pool->mFill != -1);

        final int destBaseOff = dstOff;
        final int poolEnd     = mTaps;

        int poolHead = pool.mFill;
        int newPool  = 0;

        //Fill pool.
        while( poolHead < poolEnd && srcLen > 0 ) {
            pool.mBuf[poolHead++] = src[srcOff];
            srcOff += srcStep;
            srcLen--;
        }

        if( srcLen <= 0 ) {
            return 0;
        }

        final int sourceBaseOff = srcOff;
        final int endPoint      = srcOff + srcLen * srcStep;

        while( srcOff < endPoint ) {
            dst[dstOff] = sum( mTable,
                               pool.mOffset * mTaps,
                               mTaps,
                               src,
                               srcOff,
                               sourceBaseOff,
                               pool.mBuf,
                               poolEnd,
                               srcStep );

            dstOff += dstStep;
            pool.mOffset += mInFreq;

            while( pool.mOffset >= mOutFreq ) {
                pool.mOffset -= mOutFreq;
                srcOff += srcStep;
            }
        }

        // Pretend that source has that underrun data we're not going to get.
        srcLen += ( srcOff - endPoint ) / srcStep;

        // If we didn't get enough to completely replace the pool, then shift things about a bit.
        int refill;
        if( srcLen < mTaps ) {
            refill = srcLen;
            while( refill < poolEnd ) {
                pool.mBuf[newPool++] = pool.mBuf[refill++];
            }
            refill = srcOff - srcLen * srcStep;
        } else {
            refill = srcOff - mTaps * srcStep;
        }

        // Pull in fresh pool data.
        while( refill < endPoint ) {
            pool.mBuf[newPool++] = src[refill];
            refill += srcStep;
        }

        assert( newPool > 0 );
        assert( newPool <= poolEnd );

        pool.mFill = newPool;
        return ( dstOff - destBaseOff ) / dstStep;
    }



    private static float sum( float[] scale,
                              int scaleOff,
                              int count,
                              float[] src,
                              int srcOff,
                              int triggerOff,
                              float[] reset,
                              int resetOff,
                              int srcStep )
    {
        float total = 0.0f;

        while( count-- > 0 ) {
            total += src[srcOff] * scale[scaleOff];
            if( srcOff == triggerOff ) {
                src        = reset;
                srcOff     = resetOff;
                srcStep    = 1;
                triggerOff = -100000;
            }
            srcOff -= srcStep;
            scaleOff++;
        }

        return total;
    }


    private static double iZero( double x ) {
        int n = 0;
        double u = 1.0;
        double s = 1.0;
        double t;

        do {
            n += 2;
            t = x / n;
            u *= t * t;
            s += u;
        } while( u > 1e-21 * s );

        return s;
    }


    private static void filtSinc( float[] dest, int step, double freq, double gain, int width ) {
        int off = 0;
        int len = dest.length;

        final double s = freq / step;
        final int endOff = len;
        int baseOff = 0;

        assert( width <= len );

        if( ( len & 1 ) == 0 ) {
            dest[off] = 0.0f;
            off += width;
            if( off >= endOff ) {
                off = ++baseOff;
            }
            len--;
        }

        final int mid = len / 2;
        int x = -mid;

        while( len-- > 0 ) {
            dest[off] = (float)( ( ( x != 0 ) ? Math.sin( x * Math.PI * s ) / ( x * Math.PI ) * step: freq ) * gain );
            x++;
            off += width;
            if( off >= endOff ) {
                off = ++baseOff;
            }
        }

        assert( off == width );
    }


    private static void winKaiser( float[] dest, double alpha, int width ) {
        final int endOff = dest.length;
        int len = dest.length;
        int off = 0;
        int baseOff = 0;

        assert( width <= len );

        if( ( len & 1 ) == 0 ) {
            dest[off] = 0.0f;
            off += width;
            if( off >= endOff ) {
                off = ++baseOff;
            }
            len--;
        }

        int x = -( len / 2 );
        double midsq = (double)( x - 1 ) * (double)( x - 1 );
        double iAlpha = iZero( alpha );

        while( len-- > 0 ) {
            dest[off] *= iZero( alpha * Math.sqrt( 1.0 - ( (double)x * (double)x ) / midsq ) ) / iAlpha;
            x++;
            off += width;
            if( off >= endOff ) {
                off = ++baseOff;
            }
        }

        assert( off == endOff );
    }



    private static class Pool {
        final float[] mBuf;
        int mOffset;
        int mFill;

        public Pool( int capacity ) {
            mBuf = new float[capacity];
            reset();
        }

        public void reset() {
            mOffset = 0;
            mFill   = mBuf.length / 2 + 1;
        }
    }

}
