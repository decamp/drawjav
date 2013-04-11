package cogmac.javdraw.video;

import java.io.IOException;

import cogmac.javdraw.Sink;

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
