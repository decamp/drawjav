/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import bits.jav.util.Rational;


/**
 * @author Philip DeCamp
 */
class AudioTimer {

    private int  mSampleRate  = Integer.MIN_VALUE;
    private long mScaleNum    = 1;
    private long mScaleDen    = 1;

    // The current time is defined:
    // mMicros + mDstSamps * 1000000 / mDstRate
    private long mMicros   = 0;
    private long mSamps    = 0;

    // Currently output micros.
    private long mPosMicros = 0;


    public AudioTimer() {}



    public void init( long micros, int sampleRate ) {
        mMicros = micros;
        mPosMicros = micros;

        if( mSampleRate != sampleRate ) {
            mSampleRate = sampleRate;
            int gcd   = Rational.gcd( 1000000, sampleRate );
            mScaleNum = 1000000    / gcd;
            mScaleDen = sampleRate / gcd;
        }
    }


    public void computeTimestamps( int samps, long[] out ) {
        samps += mSamps;
        long t0  = mPosMicros;
        long t1  = mMicros + Rational.rescaleRound( samps, mScaleNum, mScaleDen, Rational.AV_ROUND_DOWN );
        long rem = samps % mScaleDen;

        mMicros += Rational.rescaleRound( samps - rem, mScaleNum, mScaleDen, Rational.AV_ROUND_DOWN );
        mSamps = rem;
        mPosMicros = t1;

        out[0] = t0;
        out[1] = t1;
    }

}
