package bits.drawjav;

import bits.langx.ref.Refable;


abstract class TimedNode extends HeapNode implements Refable, Comparable<TimedNode> {
    
    public abstract int compareTo( TimedNode t );
    
    public abstract long presentationMicros();
    
    public abstract boolean ref();
    
    public abstract void deref();
    
    public abstract int refCount();

}
