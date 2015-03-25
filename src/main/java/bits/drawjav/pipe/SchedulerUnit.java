package bits.drawjav.pipe;

import bits.microtime.*;

/**
 * @author Philip DeCamp
 */
public interface SchedulerUnit extends AvUnit, Ticker {
    public int addStream( PlayClock clock, int queueCap );
}
