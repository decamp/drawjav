/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.io.IOException;

import bits.drawjav.Sink;


/**
 * @author decamp
 * @deprecated Use SinkCaster.
 */
public class IntFrameSplitter implements Sink<IntFrame> {
    
    private final Sink<IntFrame> mA;
    private final Sink<IntFrame> mB;

    
    public IntFrameSplitter(Sink<IntFrame> a, Sink<IntFrame> b) {
        mA = a;
        mB = b;
    }
    
    
    
    public void consume(IntFrame frame) throws IOException {
        mA.consume(frame);
        mB.consume(frame);
    }
    
    public void clear() {
        mA.clear();
        mB.clear();
    }
    
    public void close() throws IOException {
        mA.close();
        mB.close();
    }

    public boolean isOpen() {
        return true;
    }
    
}
