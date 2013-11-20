package bits.drawjav;

import java.io.InterruptedIOException;


/**
 * Lock for a single thread that re-implements Interrupted.
 * The issue with interrupting threads directly is that it
 * can mess up IO pretty bad, so I needed something to perform
 * interrupts in a gentler manner.
 * 
 * @author decamp
 */
public class ThreadLock {
    
    private boolean mInterrupted = false;
    
    
    /**
     * Equivalent to <code>wait()</code>.
     * 
     * @throws InterruptedIOException
     */
    public synchronized void block() throws InterruptedIOException {
        if( mInterrupted ) {
            mInterrupted = false;
            throw new InterruptedIOException();
        }
        try {
            wait();
        } catch( InterruptedException ex ) {
            throw new InterruptedIOException();
        }
        if( mInterrupted ) {
            mInterrupted = false;
            throw new InterruptedIOException();
        }
    }
    
    /**
     * Equivalent to <code>wait()</code>.
     * 
     * @param micros  Max number of milliseconds to wait.
     * @throws InterruptedIOException
     */
    public synchronized void block( long millis ) throws InterruptedIOException {
        if( mInterrupted ) {
            mInterrupted = false;
            throw new InterruptedIOException();
        }
        try {
            wait( millis );
        } catch( InterruptedException ex ) {
            throw new InterruptedIOException();
        }
        if( mInterrupted ) {
            mInterrupted = false;
            throw new InterruptedIOException();
        }
    }

    /**
     * Equivalent to <code>notify()</code>.
     */
    public synchronized void unblock() {
        notifyAll();
    }
    
    /**
     * Interrupts any thread currently blocking on this lock.
     */
    public synchronized void interrupt() {
        mInterrupted = true;
        notifyAll();
    }

    /**
     * @return true iff this lock is in an interrupted state.
     */
    public synchronized boolean isInterrupted() {
        return mInterrupted;
    }
    
    /**
     * Resets the interrupted status of this lock.
     * 
     * @return true iff this lock was in an interrupted state when this method was called.
     */
    public synchronized boolean reset() {
        boolean ret  = mInterrupted;
        mInterrupted = false;
        return ret;
    }

}
