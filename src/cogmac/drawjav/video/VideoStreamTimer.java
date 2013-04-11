package cogmac.drawjav.video;

import cogmac.jav.JavConstants;
import cogmac.jav.util.Rational;

/**
 * So, judging the presentatio+n time of a give video frame or
 * audio packet can actually be pretty tough.  This class uses
 * three separate methods to judge the correct presentation
 * time: the presentation timestamp of the JavPacket, the
 * presentation timestamp of the JavFrame, and dead reckoning,
 * assuming thet the TimeBase of the JavStream is 1/frameRate.
 * <p>
 * Uses frame pts when available.  If packet pts are available,
 * the packet pts will be used for synchronization, then relies
 * on frame rate after synced. Otherwise, uses just frame rate.
 * 
 * @author decamp
 */
public class VideoStreamTimer {
    
    private final Rational mTimeBase;
    private final Rational mMicrosPerPts;
    private final long mStartPts;
    private final long mStartMicros;
    
    private boolean mUsePacketPts = true;
    private boolean mUseFramePts  = true;
    private boolean mNeedSync     = true;
    private long mSyncPts         = 0;
    
    private long mPosPts          = 0;
    
    
    public VideoStreamTimer( Rational timeBase,
                             long startPts,
                             long startMicros )
    {
        mTimeBase     = timeBase.reduce();
        mMicrosPerPts = Rational.reduce( mTimeBase.num() * 1000000, mTimeBase.den() );
        mStartPts     = startPts;
        mStartMicros  = startMicros;
        mSyncPts      = startPts;
        mPosPts       = startPts;
    }
    
    
    
    public long startPts() {
        return mStartPts;
    }
    
    
    public long startMicros() {
        return mStartMicros;
    }
    
    
    public long ptsToMicros( long pts ) {
        if( pts == JavConstants.AV_NOPTS_VALUE ) {
            return Long.MIN_VALUE;
        }
        return ( pts - mStartPts ) * mMicrosPerPts.num() / mMicrosPerPts.den() + mStartMicros;
    }
    
    
    public long microsToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return JavConstants.AV_NOPTS_VALUE;
        }
        return ( micros - mStartMicros ) * mMicrosPerPts.den() / mMicrosPerPts.num() + mStartPts;
    }
    

    public void seekPts( long pts ) {
        mNeedSync = true;
        mSyncPts  = pts;
    }
    
    
    public void seekMicros( long micros ) {
        seekPts( microsToPts( micros ) );
    }
    
    
    public void packetSkipped( long packetPts, long packetDur, long[] outRangeMicros ) {
        packetDecoded( packetPts, packetDur, JavConstants.AV_NOPTS_VALUE, outRangeMicros );
    }
    
    
    public void packetDecoded( long packetPts, long packetDur, long framePts, long[] outRangeMicros ) {
        if( mNeedSync ) {
            sync( mSyncPts, packetPts, framePts );
        }
        
        long pts;
        
        if( mUseFramePts && framePts != JavConstants.AV_NOPTS_VALUE ) {
            pts = framePts;
        }else{
            pts = mPosPts;
        }
        
        long dur = packetDur <= 0 ? 1 : packetDur;
        mPosPts  = pts + dur;
        
        outRangeMicros[0] = ptsToMicros( pts );
        outRangeMicros[1] = ptsToMicros( pts + dur );
    }
    
    
    
    private void sync( long streamPts, long packetPts, long framePts ) {
        mNeedSync = false;
        
        if( mUseFramePts && framePts != JavConstants.AV_NOPTS_VALUE ) {
            mPosPts = framePts;
        } else if( mUsePacketPts && packetPts != JavConstants.AV_NOPTS_VALUE ) {
            mPosPts = packetPts;
        } else {
            mPosPts = streamPts;
        }
    }
    
}
