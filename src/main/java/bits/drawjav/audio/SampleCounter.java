package bits.drawjav.audio;

import bits.collect.RingList;
import bits.microtime.*;

import java.util.List;
import java.util.Queue;


/**
 * @author Philip DeCamp
 */
public class SampleCounter implements SyncClockControl {

    private final Frac mFreq;
    private final Queue<ClockEvent> mEvents = new RingList<ClockEvent>( 16 );

    private Frac mClockRate;
    private Frac mSamplesPerMicro;
    private long mBasis;
    private long mSamps;
    private long mEventMicros;
    private long mEventSamp;


    public SampleCounter( Frac frequency ) {
        mFreq = frequency;
    }



    public int addSamples( int num ) {
        long n = num;
        if( mEventSamp == Long.MIN_VALUE || mSamps + n < mEventSamp ) {
            mSamps += n;
            return num;
        }

        if( mSamps < mEventSamp ) {
            n = mEventSamp - mSamps;
            mSamps = mEventSamp;
            return (int)n;
        }

        while( true ) {
            processEvent( mEvents.remove() );
            ClockEvent next = mEvents.poll();
            if( next == null ) {
                mEventMicros = Long.MIN_VALUE;
                mEventSamp   = Long.MIN_VALUE;
                return 0;
            }

            mEventMicros = next.mExec;
            mEventSamp   = Frac.multLong( next.mExec - mBasis, mSamplesPerMicro.mNum, mSamplesPerMicro.mDen );
        }
    }


    public long micros() {
        return mBasis + Frac.multLong( mSamps, mSamplesPerMicro.mDen, mSamplesPerMicro.mNum );
    }


    public long nextEventMicros() {
        if( mEventSamp == Long.MIN_VALUE ) {
            return Long.MIN_VALUE;
        }
        return mEventMicros;
    }


    public long sample() {
        return mSamps;
    }


    public long nextEventSampl() {
        return mEventSamp;
    }


    public Frac clockRate() {
        return new Frac( mClockRate );
    }


    public Frac sampleRate() {
        Frac ret = new Frac();
        Frac.multFrac( mSamplesPerMicro.mNum, mSamplesPerMicro.mDen, 1000000, 1, ret );
        return ret;
    }


    @Override
    public void clockStart( long execMicros ) {
        addEvent( ClockEvent.createClockStart( null, execMicros ) );
    }

    @Override
    public void clockStop( long execMicros ) {
        addEvent( ClockEvent.createClockStop( null, execMicros ) );
    }

    @Override
    public void clockSeek( long execMicros, long seekMicros ) {
        addEvent( ClockEvent.createClockSeek( null, execMicros, seekMicros ) );
    }

    @Override
    public void clockRate( long execMicros, Frac rate ) {
        addEvent( ClockEvent.createClockRate( null, execMicros, rate ) );
    }



    private void addEvent( ClockEvent event ) {
        mEvents.add( event );
        if( mEvents.size() < 2048 ) {
            return;
        }

    }


    private void processEvent( ClockEvent event ) {

    }

}
