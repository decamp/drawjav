package bits.drawjav;

import bits.jav.Jav;
import bits.jav.util.Rational;

public class PacketTimer {

    private static final long NAN = Jav.AV_NOPTS_VALUE;
    
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
        mMicrosPerPts  = Rational.reduce( timeBase.num() * 1000000, timeBase.den() );
        init( startPts, startMicros );
    }
    
    
    
    public void init( long startPts, long startMicros ) {
        mStartPts      = startPts;
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
        return ( pts - mStartPts ) * mMicrosPerPts.num() / mMicrosPerPts.den() + mStartMicros;
    }
    
    
    public long microsToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return NAN;
        }
        return ( micros - mStartMicros ) * mMicrosPerPts.den() / mMicrosPerPts.num() + mStartPts;
    }
    
    
    public long bestEffortPtsToMicros( long pts ) {
        if( pts == NAN ) {
            return Long.MIN_VALUE;
        }
        return ( pts - mOffsetPts ) * mMicrosPerPts.num() / mMicrosPerPts.den() + mOffsetMicros;
    }
    

    public long bestEffortMicrosToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return NAN;
        }
        return ( micros - mOffsetMicros ) * mMicrosPerPts.den() / mMicrosPerPts.num() + mOffsetPts;
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
