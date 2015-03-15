package bits.drawjav.pipe;

import bits.drawjav.Stream;
import bits.drawjav.StreamFormat;
import bits.util.ref.Refable;


/**
 * @author Philip DeCamp
 */
abstract class InPadAdapter<T extends Refable> implements InPad<T> {

    @Override
    public int status() {
        return OKAY;
    }

    @Override
    public int offer( T packet ) {
        return OKAY;
    }

    @Override
    public void config( StreamFormat format ) {}

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
