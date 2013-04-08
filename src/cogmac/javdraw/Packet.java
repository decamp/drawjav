package cogmac.javdraw;

import cogmac.jav.JavConstants;
import cogmac.langx.ref.Refable;
import cogmac.microtime.TimeRanged;

/**
 * @author decamp
 */
public interface Packet extends Refable, TimeRanged {
    
    public static final int TYPE_UNKNOWN = JavConstants.AVMEDIA_TYPE_UNKNOWN;
    public static final int TYPE_AUDIO   = JavConstants.AVMEDIA_TYPE_AUDIO;
    public static final int TYPE_VIDEO   = JavConstants.AVMEDIA_TYPE_VIDEO;
    
    public StreamHandle stream();
    
    public long getStartMicros();
    public long getStopMicros();
    
    public boolean ref();
    public void deref();
    public int refCount();
}
