/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.nio.channels.ClosedChannelException;


@SuppressWarnings( "unchecked" )
class TimedMultiQueue<T extends TimedNode> {
    
    private PrioHeap<Channel<T>> mChannels;
    private boolean mClosed   = false;
    private boolean mShutdown = true;
    
    
    public TimedMultiQueue() {
        mChannels = new PrioHeap<Channel<T>>();
    }

    
    
    public synchronized Object openChannel() throws ClosedChannelException {
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        
        Channel<T> chan = new Channel<T>();
        mChannels.offer( chan );
        if( chan == mChannels.peek() ) {
            notify();
        }
        
        return chan;
    }
    

    public synchronized void clearChannel( Object channel ) {
        Channel<T> q = (Channel<T>)channel;
        if( q.isEmpty() ) {
            return;
        }
        mChannels.remove( q );
        while( !q.isEmpty() ) {
            q.remove().deref();
        }
        mChannels.offer( q );
    }
    
    
    public synchronized void closeChannel( Object channel ) {
        Channel<T> q = (Channel<T>)channel;
        if( q.mClosed ) {
            return;
        }
        q.mClosed = true;
    }
    
    
    public synchronized void forceCloseChannel( Object channel ) {
        Channel<T> q = (Channel<T>)channel;
        q.mClosed = true;
        mChannels.remove( q );
        while( !q.isEmpty() ) {
            q.remove().deref();
        }
    }

    
    public synchronized boolean hasChannel() {
        return mChannels.size() > 0;
    }
    
    
    public synchronized T remove() {
        while( true ) {
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
            return ret;
        }
    }

    
    public synchronized void offer( Object channel, T item ) throws ClosedChannelException {
        Channel<T> c = (Channel<T>)channel;
        if( c.mClosed ) {
            throw new ClosedChannelException();
        }
        
        c.offer( item );
        item.ref();
        reschedule( c );
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
    
    
    private synchronized void reschedule( Channel<T> c ) {
        // Check if head changes during rescheduling. If so, notify scheduling thread.
        Channel<T> oldHead = mChannels.peek();
        mChannels.reschedule( c );
        if( oldHead != mChannels.peek() || oldHead == c ) {
            notify();
        }
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
    
}

