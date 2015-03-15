package bits.drawjav.pipe;

import bits.drawjav.StreamFormat;
import bits.util.ref.Refable;

import java.io.IOException;


/**
 * @author Philip DeCamp
 */
public class OutPadAdapter implements OutPad {

    @Override
    public int status() {
        return EXCEPTION;
    }

    @Override
    public int poll( Refable[] out ) {
        return FILL_FILTER;
    }

    @Override
    public void config( StreamFormat format ) throws IOException {}

    @Override
    public boolean isThreaded() {
        return false;
    }

    @Override
    public Object lock() {
        return null;
    }

    @Override
    public Exception exception() {
        return null;
    }

}
