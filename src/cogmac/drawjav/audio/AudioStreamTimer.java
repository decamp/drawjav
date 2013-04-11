package cogmac.drawjav.audio;

import cogmac.jav.JavConstants;
import cogmac.jav.util.Rational;

/**
 * Has two steps: <br/>
 * 1. A syncronization step that, based on calibration, recomputes starting time after a seek or disruption in
 *    stream.  This occurs on the first frame after each seek operation.
 * 2. A timeing step that assigns time range to individual audio packets.  Occurs on each packet. 
 * 
 * @author decamp
 */
public class AudioStreamTimer {
    
    private final Rational mTimeBase;
    private final long mStartPts;
    private final long mStartMicros;
    private final long mFreq;
    private final long mChannels;
    private final Rational mMicrosPerPts;
    
    private boolean mNeedSync = true;
    private long mSyncMicros  = 0;
    
    private long mPosMicros  = 0;
    private long mPosSamples = 0;
    
    
    public AudioStreamTimer( Rational timeBase,
                             long startPts,
                             long startMicros,
                             long frequency, 
                             long channels) 
    {
        mTimeBase     = timeBase.reduce();
        mStartPts     = startPts;
        mStartMicros  = startMicros;
        mFreq         = frequency;
        mChannels     = channels;
        mMicrosPerPts = Rational.reduce( mTimeBase.num() * 1000000, mTimeBase.den() );
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
        return (pts - mStartPts) * mMicrosPerPts.num() / mMicrosPerPts.den() + mStartMicros;
    }
    
    
    public long microsToPts( long micros ) {
        if( micros == Long.MIN_VALUE ) {
            return JavConstants.AV_NOPTS_VALUE;
        }
        return (micros - mStartMicros) * mMicrosPerPts.den() / mMicrosPerPts.num() + mStartPts;
    }
    

    public void seekPts( long pts ) {
        seekMicros( ptsToMicros( pts ) );
    }
    
    
    public void seekMicros( long micros ) {
        mNeedSync   = true;
        mSyncMicros = micros;
    }
    
    
    public void packetSkipped( long packetPts, long packetDur, long[] outRangeMicros ) {
        if( packetDur < 0 ) {
            packetDur = 1;
        }
        long count = packetDur * mFreq * mChannels * mTimeBase.num() / mTimeBase.den();
        packetDecoded( packetPts, count, outRangeMicros );
    }
    
    
    public void packetDecoded( long packetPts, long sampleCount, long[] outRangeMicros ) {
        if( mNeedSync ) {
            sync( mSyncMicros, packetPts );
        }
        if( mPosMicros == Long.MIN_VALUE ) {
            outRangeMicros[0] = Long.MIN_VALUE;
            outRangeMicros[1] = Long.MIN_VALUE;
            return;
        }
        
        long rate = mChannels * mFreq;
        outRangeMicros[0] = mPosMicros + mPosSamples * 1000000L / rate;
        mPosSamples += sampleCount;
        outRangeMicros[1] = mPosMicros + mPosSamples * 1000000L / rate;
        
        mPosMicros += (mPosSamples / rate) * 1000000L;
        mPosSamples %= rate;
    }
    
    
    
    private void sync( long micros, long packetPts ) {
        mNeedSync = false;
        
        if( packetPts != JavConstants.AV_NOPTS_VALUE ) {
            mPosMicros = ptsToMicros( packetPts );
        } else if( micros != Long.MIN_VALUE ) {
            mPosMicros = micros;
        } else {
            mPosMicros = Long.MIN_VALUE;
        }
        
        mPosSamples = 0;
    }
    
}
