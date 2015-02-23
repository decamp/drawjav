package bits.drawjav.pipe;

import bits.drawjav.StreamHandle;
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
    public void config( StreamHandle stream ) {}

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
