/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.jav.Jav;
import bits.jav.util.Rational;

class PacketTimer {

    private static final long NAN = Jav.AV_NOPTS_VALUE;
    private static final Rational MICROS = new Rational( 1, 1000000 );

    // If the stream start offset is smaller than this,
    // assume it actually starts at 0.
    private static final long STREAM_START_THRESHOLD = 500000L;

    private final Rational mTimeBase;
    private final Rational mMicrosPerPts;

    // Changed only on init()
    private long mStartPts;
    private long mStartMicros;
    private long mSyncThreshPts;
    private long mSyncThreshMicros = 2000000L;

    // Estimates of acutal start pts and micros.
    private long mOffsetPts;
    private long mOffsetMicros;

    // Current position
    private long mPosPts = 0;

    // Indicates seek was performed and timestamps need to be recomputed.
    private boolean mNeedSync = true;
    private long mSyncPts;


    public PacketTimer( Rational timeBase ) {
        this( timeBase, 0, 0 );
    }

    
    public PacketTimer( Rational timeBase, long startPts, long startMicros ) {
        mTimeBase     = timeBase.reduce();
        mMicrosPerPts = Rational.reduce( timeBase.num() * 1000000, timeBase.den() );
        init( startPts, startMicros );
    }
    
    
    
    public void init( long startPts, long startMicros ) {
//        long rawStart = Rational.rescale( startPts, mTimeBase.num() * 1000000, mTimeBase.den() );
//        if( Math.abs( rawStart ) < STREAM_START_THRESHOLD ) {
//            startPts = 0;
//        }

        mStartPts      = startPts;
        mSyncPts       = startPts;
        mOffsetPts     = startPts;
        mPosPts        = startPts;
        mStartMicros   = startMicros;
        mOffsetMicros  = startMicros;
        mSyncThreshPts = mSyncThreshMicros * mMicrosPerPts.den() / mMicrosPerPts.num();
    }
    
    
    public long startPts() {
        return mOffsetPts;
    }
    
    
    public long startMicros() {
        return mOffsetMicros;
    }
    
    
    public long ptsToMicros( long pts ) {
        if( pts == NAN ) {
            return Long.MIN_VALUE;
        }
        return Rational.rescaleQ( pts - mStartPts, mTimeBase, MICROS ) + mStartMicros;
    }
    
    
    public long microsToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return NAN;
        }
        return Rational.rescaleQ( micros - mStartMicros, MICROS, mTimeBase ) + mStartPts;
    }
    
    
    public long bestEffortPtsToMicros( long pts ) {
        if( pts == NAN ) {
            return Long.MIN_VALUE;
        }
        return Rational.rescaleQ( pts - mOffsetPts, mTimeBase, MICROS ) + mOffsetMicros;
    }
    

    public long bestEffortMicrosToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return NAN;
        }
        return Rational.rescaleQ( micros - mOffsetMicros, MICROS, mTimeBase ) + mOffsetMicros;
    }
    
    
    public void seekPts( long pts ) {
        mNeedSync = true;
        mSyncPts  = pts;
    }
    
    
    public void seekMicros( long micros ) {
        seekPts( microsToPts( micros ) );
    }
    
    
    public void packetSkipped( long posPts, long durPts, long[] outRangeMicros ) {
        packetDecoded( posPts, durPts, outRangeMicros );
    }
    
    
    public void packetDecoded( long posPts, long durPts, long[] outRangeMicros ) {
        if( mNeedSync ) {
            sync( mSyncPts, posPts );
        }
        
        // If no duration, assume 1 pts.
        if( durPts <= 0 ) {
            durPts = 1;
        }
        
        // If no pts, use dead reckoning.
        if( posPts == NAN ) {
            posPts = mPosPts;
        }
        
        mPosPts = posPts + durPts;
        if( outRangeMicros != null ) {
            outRangeMicros[0] = bestEffortPtsToMicros( posPts );
            outRangeMicros[1] = bestEffortPtsToMicros( posPts + durPts );
        }
    }

    
    
    private void sync( long syncPts, long posPts ) {
        mNeedSync = false;
        
        // Check if seek was for before file start.
        if( syncPts < mStartPts ) {
            syncPts = mStartPts;
        }
        
        // Check if we have any position.
        if( posPts == NAN ) {
            // No position. Just use dead reckoning using syncPts as our starting point.
            mPosPts       = syncPts;
            mOffsetPts    = mStartPts;
            mOffsetMicros = mStartMicros;
            return;
        }
        
        // Check if pts is way off.
        if( posPts < syncPts - mSyncThreshPts ||
            posPts > syncPts + mSyncThreshPts ) 
        {
            // Assume timestamps are wrong and correct offset.
            mPosPts       = syncPts;
            mOffsetPts    = posPts;
            mOffsetMicros = ptsToMicros( syncPts );
        }
    
        // Seems to check out. 
        mPosPts       = posPts;
        mOffsetPts    = mStartPts;
        mOffsetMicros = mStartMicros;
    }


}
