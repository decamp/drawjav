package bits.drawjav;

import bits.langx.ref.Refable;
import bits.microtime.TimeRanged;

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
