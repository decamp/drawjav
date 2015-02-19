package bits.drawjav;

import bits.microtime.*;

import java.util.*;


/**
 * @author Philip DeCamp
 */
public class ClockEventQueue implements SyncClockControl {


    public ClockEventQueue( int maxCap ) {}

    @Override
    public void clockStart( long execMicros ) {

    }

    @Override
    public void clockStop( long execMicros ) {

    }

    @Override
    public void clockSeek( long execMicros, long seekMicros ) {

    }

    @Override
    public void clockRate( long execMicros, Frac rate ) {

    }



    public ClockEvent peek() {
        return null;
    }


    public ClockEvent poll() {
        return null;
    }


    public ClockEvent remove() {
        return null;
    }

}
