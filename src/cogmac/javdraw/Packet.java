package cogmac.javdraw;

import cogmac.langx.ref.Refable;
import cogmac.microtime.TimeRanged;

/**
 * @author decamp
 */
public interface Packet extends Refable, TimeRanged {
    
    public StreamHandle stream();
    
    public long getStartMicros();
    public long getStopMicros();
    
    public boolean ref();
    public void deref();
    public int refCount();
}
