package bits.drawjav;

import bits.drawjav.pipe.ClearUnitEvent;
import bits.microtime.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * @author Philip DeCamp
 */
public class ClockEventQueue implements SyncClockControl {


    private final Object mLock;
    private final PlayClock mClock;
    private final Queue<Object> mQueue = new LinkedList<Object>();


    public ClockEventQueue( Object lock, PlayClock clock, int maxCap ) {
        mLock  = lock;
        mClock = clock;

        if( clock != null ) {
            clock.addListener( this );
        }
    }


    @Override
    public void clockStart( long exec ) {
        offer( ClockEvent.createClockStart( this, exec ) );
    }

    @Override
    public void clockStop( long exec ) {
        offer( ClockEvent.createClockStop( this, exec ) );
    }

    @Override
    public void clockSeek( long exec, long seek ) {
        synchronized( this ) {
            offer( ClockEvent.createClockSeek( this, exec, seek ) );
            // TODO: Clear logic should probably not be here.
            offer( ClearUnitEvent.INSTANCE );
        }
    }

    @Override
    public void clockRate( long exec, Frac rate ) {
        Object a;
        Object b;

        synchronized( mClock ) {
            a = ClockEvent.createClockRate( this, exec, rate );
            // TODO: For accuracy, this call should actually be:
            // createClockSeek( this, mClock.masterBasis(), mClock.timeBasis() );
            // However, some units may not deal well with backdated seeks.
            b = ClockEvent.createClockSeek( this, mClock.masterMicros(), mClock.micros() );
        }

        synchronized( this ) {
            offer( a );
            offer( b );
            offer( ClearUnitEvent.INSTANCE );
        }
    }


    public void offer( Object event ) {
        synchronized( mLock ) {
            if( mQueue.isEmpty() ) {
                mLock.notifyAll();
            }
            mQueue.offer( event );
        }
    }


    public Object peek() {
        synchronized( mLock ) {
            return mQueue.peek();
        }
    }


    public Object poll() {
        synchronized( mLock ) {
            return mQueue.poll();
        }
    }


    public Object remove() {
        synchronized( mLock ) {
            return mQueue.remove();
        }
    }

}
