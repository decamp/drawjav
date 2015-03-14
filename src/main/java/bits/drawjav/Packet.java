/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.microtime.TimeRanged;
import bits.util.ref.Refable;

/**
 * @author decamp
 */
public interface Packet extends Refable, TimeRanged {
    public Stream stream();
    
    public long startMicros();
    public long stopMicros();

    public boolean ref();
    public void deref();
    public int refCount();
}
