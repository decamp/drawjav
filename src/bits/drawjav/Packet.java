package bits.drawjav;

import bits.microtime.TimeRanged;
import bits.util.ref.Refable;

/**
 * @author decamp
 */
public interface Packet extends Refable, TimeRanged {
    public StreamHandle stream();
    
    public long startMicros();
    public long stopMicros();
    
    public boolean ref();
    public void deref();
    public int refCount();
}
