package bits.drawjav.pipe;

import java.util.concurrent.*;


/**
 * @author Philip DeCamp
 */
class ExecutionQueue implements Executor {

    private BlockingQueue<Runnable> mDeque = new LinkedBlockingQueue<Runnable>();

    @Override
    public void execute( Runnable runnable ) {
        mDeque.offer( runnable );
    }

    Runnable poll() {
        return mDeque.poll();
    }

}
