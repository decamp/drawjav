package cogmac.javdraw;

import cogmac.langx.ref.Refable;


public abstract class TimedNode extends DoubleLinkedNode implements Refable, Comparable<TimedNode> {
    
    public abstract int compareTo( TimedNode t );
    
    public abstract long presentationMicros();
    
    public abstract boolean ref();
    
    public abstract void deref();
    
    public abstract int refCount();

}
