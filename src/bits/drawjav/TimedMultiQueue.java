package bits.drawjav;

import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.math3d.Arr;


@SuppressWarnings( "unchecked" )
class TimedMultiQueue<T extends TimedNode> {
    
    private PrioHeap<Channel<T>> mChannels;
    private boolean mClosed   = false;
    private boolean mShutdown = true;
    
    
    public TimedMultiQueue() {
        mChannels = new PrioHeap<Channel<T>>();
    }

    
    public synchronized Object openChannel() throws ClosedChannelException {
        checkHeap();
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        
        Channel<T> chan = new Channel<T>();
        mChannels.offer( chan );
        if( chan == mChannels.peek() ) {
            notify();
        }
        
        checkHeap();
        return chan;
    }
    

    public synchronized void clearChannel( Object channel ) {
        checkHeap();
        Channel<T> q = (Channel<T>)channel;
        if( q.isEmpty() ) {
            return;
        }
        mChannels.remove( q );
        while( !q.isEmpty() ) {
            q.remove().deref();
        }
        mChannels.offer( q );
        checkHeap();
    }
    
    
    public synchronized void closeChannel( Object channel ) {
        checkHeap();
        Channel<T> q = (Channel<T>)channel;
        if( q.mClosed ) {
            return;
        }
        q.mClosed = true;
        checkHeap();
    }
    
    
    public synchronized void forceCloseChannel( Object channel ) {
        checkHeap();
        Channel<T> q = (Channel<T>)channel;
        q.mClosed = true;
        mChannels.remove( q );
        while( !q.isEmpty() ) {
            q.remove().deref();
        }
        notify();
        checkHeap();
    }

    
    public synchronized boolean hasChannel() {
        return mChannels.size() > 0;
    }
    
    
    public synchronized T remove() {
        while( true ) {
            checkHeap();
            
            // Check if there is command to return from end of last loop.
            // This is performed here to avoid placing a second synchronization
            // black at end of loop.
            Channel<T> channel = mChannels.peek();

            if( channel == null ) {
                // If there are no queues, check if closed, or wait indefinitely.
                if( mClosed ) {
                    mShutdown = true;
                    return null;
                }

                try {
                    wait();
                } catch( InterruptedException ex ) {}
                continue;
            }

            // Find next command to process.
            T ret = channel.peek();
            if( ret == null ) {
                if( channel.mClosed || mClosed ) {
                    // Channel is shutdown. Remove.
                    channel.mClosed = true;
                    mChannels.remove( channel );
                    continue;
                }
                try {
                    wait();
                } catch( InterruptedException ex ) {}
                continue;
            }
            
            // Determine execution time of command.
            long t = ret.presentationMicros();
            // Objects with presentationMicros == Long.MIN_VALUE are executed immediately.
            if( t > Long.MIN_VALUE ) {
                long waitMillis = t / 1000L - System.currentTimeMillis();
                if( waitMillis > 10L ) {
                    try {
                        wait( waitMillis );
                    } catch( InterruptedException ex ) {}
                    continue;
                }
            }

            // Remove command from queue.
            channel.remove();
            mChannels.reschedule( channel );
            checkHeap();
            return ret;
        }
    }

    
    public synchronized void offer( Object channel, T item ) throws ClosedChannelException {
        checkHeap();
        Channel<T> c = (Channel<T>)channel;
        if( c.mClosed ) {
            throw new ClosedChannelException();
        }
        
        c.offer( item );
        item.ref();
        reschedule( c );
        checkHeap();
    }
        
    
    public synchronized void wakeup() {
        notify();
    }
    
    
    public synchronized void close() {
        if( mClosed ) {
            return;
        }
        mClosed = true;
        notify();
    }
    
    
    public synchronized void forceClose() {
        mClosed = true;
        while( !mChannels.isEmpty() ) {
            PrioHeap<T> c = mChannels.remove();
            while( !c.isEmpty() ) {
                c.remove().deref();
            }
        }
        notify();
    }

    
    public synchronized boolean isOpen() {
        return !mClosed;
    }
    
    
    public synchronized boolean isShutdown() {
        return mShutdown;
    }
    

    private void checkHeap() {
        checkHeap( null );
    }
    
    
    private void checkHeap( Channel<T> c ) {
        int s = mChannels.size();
        for( int i = s - 1; i > 1; i-- ) {
            if( mChannels.get( i ).size() > 0 && mChannels.get( (i-1)/2 ).size() == 0 ) {
                System.out.println( "??????????????" );
                checkHeap2( c );
                return;
            }
        }

        Arrays.fill( BACKUP.mArr, null );
        Arrays.fill( BACKUP_INT.mArr, null );
        for( int i = 0; i < s; i++ ) {
            BACKUP.mArr[i]     = mChannels.mArr[i];
            BACKUP_INT.mArr[i] = new Val( ((Channel<T>)mChannels.mArr[i]).size() );
        }
        
        BACKUP.mSize = s;
        BACKUP_INT.mSize = s;
    }

    
    private PrioHeap<Channel<T>> BACKUP = new PrioHeap<Channel<T>>();
    private PrioHeap<Val> BACKUP_INT    = new PrioHeap<Val>();
    
    
    private void checkHeap2( Channel<T> c ) {
        int s = BACKUP.size();
        for( int i = 0; i < s; i++ ) {
            BACKUP.mArr[i].mHeapIndex = i;
        }
        
        BACKUP.reschedule( c );
    }
    
    
    
    private synchronized void reschedule( Channel<T> c ) {
        // Check if head changes during rescheduling. If so, notify scheduling thread.
        Channel<T> oldHead = mChannels.peek();
        mChannels.reschedule( c );
        if( oldHead != mChannels.peek() || oldHead == c ) {
            notifyAll();
        }
        checkHeap( c );
    }
    

    private static class Channel<T extends TimedNode> extends PrioHeap<T> implements Comparable<Channel<T>> {
        
        boolean mClosed = false;
        
        public Channel() {}

        @Override
        public int compareTo( Channel<T> c ) {
            TimedNode ta = peek();
            TimedNode tb = c.peek();
            
            return ta == null ? 
                 ( tb == null ?  0 : 1 ) :
                 ( tb == null ? -1 : ta.compareTo( tb ) );
        }
        
        
        public String toString() {
            return "" + size(); 
        }
        
    }
    


    private static class Val extends HeapNode implements Comparable<Val> {
        
        public int mNum;
        
        public Val( int num ) {
            mNum = num;
        }
        
        public int compareTo( Val v ) {
            return mNum < v.mNum ? -1 :
                   mNum > v.mNum ?  1 : 0;
            
        }

        public String toString() {
            return "" + mNum;
        }
        
    }

}

