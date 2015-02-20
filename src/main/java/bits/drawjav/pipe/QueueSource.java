package bits.drawjav.pipe;

import bits.collect.RingList;
import bits.drawjav.Packet;

import java.util.Queue;


/**
 * @author Philip DeCamp
 */
public class QueueSource<T extends Packet> implements SourcePad<T> {

    private final Queue<T> mQueue;
    private final int      mMaxCap;


    public QueueSource( int capacity ) {
        if( capacity <= 0 ) {
            capacity = 0;
        }
        mMaxCap = capacity;
        mQueue  = new RingList<T>( capacity <= 0 ? 16 : capacity );
    }


    public synchronized void offer( T packet ) {
        if( mMaxCap > 0 ) {
            while( mQueue.size() > mMaxCap ) {
                mQueue.remove().deref();
            }
        }
        notifyAll();
        mQueue.offer( packet );
        packet.ref();
    }

    @Override
    public synchronized FilterErr remove( T[] out, long blockMicros ) {
        T ret = mQueue.poll();
        if( ret != null ) {
            out[0] = ret;
            return FilterErr.DONE;
        }

        if( blockMicros <= 0 ) {
            return FilterErr.UNDERFLOW;
        }

        long now = System.currentTimeMillis();
        long end = now + blockMicros / 1000L;
        while( end - now > 10L ) {
            try {
                wait( blockMicros / 1000L );
            } catch( InterruptedException e ) {
                return FilterErr.UNDERFLOW;
            }

            ret = mQueue.poll();
            if( ret != null ) {
                out[0] = ret;
                return FilterErr.DONE;
            }
            now = System.currentTimeMillis();
        }

        return FilterErr.TIMEOUT;
    }

    @Override
    public synchronized int available() {
        return mQueue.size();
    }

    @Override
    public Exception exception() {
        return null;
    }

}
