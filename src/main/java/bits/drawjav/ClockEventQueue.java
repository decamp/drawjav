package bits.drawjav;

import bits.microtime.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * @author Philip DeCamp
 */
public class ClockEventQueue implements SyncClockControl {


    private final Queue<ClockEvent> mQueue = new LinkedList<ClockEvent>();


    public ClockEventQueue( int maxCap ) {}

    @Override
    public synchronized void clockStart( long exec ) {
        mQueue.offer( ClockEvent.createClockStart( this, exec ) );
    }

    @Override
    public synchronized void clockStop( long exec ) {
        mQueue.offer( ClockEvent.createClockStop( this, exec ) );
    }

    @Override
    public synchronized void clockSeek( long exec, long seek ) {
        mQueue.offer( ClockEvent.createClockSeek( this, exec, seek ) );
    }

    @Override
    public synchronized void clockRate( long exec, Frac rate ) {
        mQueue.offer( ClockEvent.createClockRate( this, exec, rate ) );
    }


    public synchronized ClockEvent peek() {
        return mQueue.peek();
    }


    public synchronized ClockEvent poll() {
        return mQueue.poll();
    }


    public synchronized ClockEvent remove() {
        return mQueue.remove();
    }

}
