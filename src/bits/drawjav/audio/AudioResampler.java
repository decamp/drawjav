package bits.drawjav.audio;

import bits.jav.util.Rational;


/**
 * Based on audio resampler used in Vorbis Tools. <br/>  
 * Supports arbitrary sample-rate conversion. <br/>
 * Uses kaiser-windowed sinc filter.  
 */
public class AudioResampler {
    
    private final int mChannels;
    private final int mInFreq;
    private final int mOutFreq;
    private final int mTaps;
    private final float[] mTable;
    private final float[] mTail;
    private final Pool[] mPools;

    /**
     * @param inFreq        Input frequency of resampler. Must be greater than zero.
     * @param outFreq       Output frequency of resampler. Must be greater than zero.
     * @param numChannels   Number of channels of audio signal. All audio sample arres are interleaved. Must be greater than zero.
     **/
    public AudioResampler( int inFreq, int outFreq, int numChannels ) {
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
    public AudioResampler( int inFreq, 
                           int outFreq, 
                           int numChannels, 
                           double gain, 
                           double cutoff, 
                           int taps, 
                           double beta ) 
    {
        assert( numChannels > 0 );
        assert( outFreq > 0 );
        assert( inFreq > 0 );
        assert( cutoff >= 0.01 && cutoff <= 1.0 );
        assert( taps > 2 && taps <= 1000 );
        assert( beta > 2.0 );
        
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
     * @param inputSize  Number of possible input samples.
     * @return the recommended size of output buffer to use for processing input.
     */
    public int recommendOutBufferSize( int inputSize ) {
        //The 16 * mChannels provides a bit of leeway.
        return inputSize * mOutFreq / mInFreq + 16 * mChannels;
    }
        
    /**
     * @return the recommended size of output buffer to use for draining samples.
     */
    public int recommendDrainBufferSize() {
        return recommendOutBufferSize(mChannels * (mTaps / 2));
    }
        
    /**
     * @param outSize Size of an output buffer.
     * @return the maximum number of input samples that can be processed and
     * stored in a buffer of maxOutput samples.
     */
    public int recommendInBufferSize( int outSize ) {
        int n = ( outSize - 16 * mChannels ) * mInFreq / mOutFreq;
        return Math.max( 0, n );
    }
    
    /**
     * Resamples the some number of input samples and places them into an
     * output array.
     * 
     * @param in      Input array containing samples (interleaved if multichannel).
     * @param inOff   Offset into input array
     * @param out     Output buffer
     * @param outOff  Offset into output array
     * @param len     Number of input FRAMES to process, NOT number of SAMPLES.  
     *                The number of samples must be a multiple of the channel number,
     *                (len % channels() == 0), or an IllegalArgumentException will be thrown.
     * @return the number of output frames generated (interleaved if multiple channels)
     */
    public int processSamples( float[] in, int inOff, float[] out, int outOff, int len ) {
        if( len % mChannels != 0 ) {
            throw new IllegalArgumentException( "Number of samples not evenly divisible by number of channels (len % numChannels) != 0" );
        }
        
        int ret = 0;
        for( int i = 0; i < mChannels; i++ ) {
            ret = push( mPools[i], in, 
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
     * Drains all buffered data into an output buffer. Multichannel data
     * will be interleaved.
     * 
     * @param out     Output buffer.
     * @param outOff  Offset into output buffer.
     * @return the number of samples placed into the output buffer.
     */
    public int drain( float[] out, int outOff ) {
        //assert(state->pools->mFill >= 0);
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
                      float[] source, 
                      int sourceOff, 
                      int sourceStep,
                      float[] dest, 
                      int destOff, 
                      int destStep, 
                      int sourceLength )
    {
//      assert(pool->mFill);
//      assert(dest);
//      assert(source);
//      assert(pool->mFill != -1);
        
        final int destBaseOff = destOff;
        final int poolEnd     = mTaps;
        
        int poolHead = pool.mFill;
        int newPool  = 0;
        
        //Fill pool.
        while( poolHead < poolEnd && sourceLength > 0 ) {
            pool.mBuf[poolHead++] = source[sourceOff];
            sourceOff += sourceStep;
            sourceLength--;
        }

        if( sourceLength <= 0 ) {
            return 0;
        }

        final int sourceBaseOff = sourceOff;
        final int endPoint = sourceOff + sourceLength * sourceStep;
        
        while( sourceOff < endPoint ) {
            dest[destOff] = sum( mTable, pool.mOffset * mTaps, mTaps,
                                 source, sourceOff, sourceBaseOff,
                                 pool.mBuf, poolEnd, sourceStep);

            destOff += destStep;
            pool.mOffset += mInFreq;

            while( pool.mOffset >= mOutFreq ) {
                pool.mOffset -= mOutFreq;
                sourceOff += sourceStep;
            }
        }

        //assert(destOff == destBaseOff + lencheck * destStep);

        // Pretend that source has that underrun data we're not going to get.
        sourceLength += ( sourceOff - endPoint ) / sourceStep;

        // If we didn't get enough to completely replace the pool, then shift things about a bit.
        int refill;
        if( sourceLength < mTaps ) {
            refill = sourceLength;
            while( refill < poolEnd ) {
                pool.mBuf[newPool++] = pool.mBuf[refill++];
            }
            refill = sourceOff - sourceLength * sourceStep;
        } else {
            refill = sourceOff - mTaps * sourceStep;
        }

        // Pull in fresh pool data.
        while( refill < endPoint ) {
            pool.mBuf[newPool++] = source[refill];
            refill += sourceStep;
        }

        assert( newPool > 0 );
        assert( newPool <= poolEnd );

        pool.mFill = newPool;
        return ( destOff - destBaseOff ) / destStep;
    }

   
    
    private static float sum( float[] scale, int scaleOff, int count,
                              float[] source, int sourceOff, int triggerOff,
                              float[] reset, int resetOff, int sourceStep)
    {
        float total = 0.0f;

        while( count-- > 0 ) {
            total += source[sourceOff] * scale[scaleOff];
            if( sourceOff == triggerOff ) {
                source = reset;
                sourceOff = resetOff;
                sourceStep = 1;
                triggerOff = -100000;
            }
            
            sourceOff -= sourceStep;
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
