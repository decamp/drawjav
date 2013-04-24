package cogmac.drawjav;

import cogmac.jav.JavConstants;
import cogmac.jav.util.Rational;

public class PacketTimer {

    private static final long NAN = JavConstants.AV_NOPTS_VALUE;
    
    private final Rational mMicrosPerPts;
    private final long mStartPts;
    private final long mStartMicros;
    private final long mSyncThreshPts;
    private final long mSyncThreshMicros = 2000000L;
    
    private long mOffsetPts;
    private long mOffsetMicros;
    
    private boolean mNeedSync = true;
    private long mSyncPts;
    
    private long mPosPts = 0;
    
    
    public PacketTimer( Rational timeBase, long firstPts, long firstMicros ) {
        mMicrosPerPts  = Rational.reduce( timeBase.num() * 1000000, timeBase.den() );
        mStartPts      = firstPts;
        mOffsetPts     = firstPts;
        mPosPts        = firstPts;
        mStartMicros   = firstMicros;
        mOffsetMicros  = firstMicros;
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
