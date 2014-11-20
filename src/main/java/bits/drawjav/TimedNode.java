/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.util.ref.Refable;


abstract class TimedNode extends HeapNode implements Refable, Comparable<TimedNode> {
    
    public abstract int compareTo( TimedNode item );
    
    public abstract long presentationMicros();
    
    public abstract boolean ref();
    
    public abstract void deref();
    
    public abstract int refCount();

}
