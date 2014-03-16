package bits.drawjav;

import java.io.*;

import bits.collect.ConcurrentBag;

/**
 * Threadsafe structure for sending method calls to multiple sinks.
 * 
 * @author decamp
 */
public class SinkCaster<T> implements Sink<T> {
    
    protected final ConcurrentBag<Sink<? super T>> mSinks = new ConcurrentBag<Sink<? super T>>();
    private boolean mClosed = false;
    
    
    public SinkCaster() {}
    
    
    
    public void consume( T frame ) throws IOException {
        IOException err = null;
        ConcurrentBag.Node<Sink<? super T>> head = mSinks.head();
        
        while( head != null ) {
            try {
                head.mItem.consume( frame );
            } catch( InterruptedIOException ex ) {
                throw ex;
            } catch( IOException ex ) {
                err = IOExceptionList.join( err, ex ); 
            }
            
            head = head.mNext;
        }
        
        if(err != null) {
            throw err;
        }
    }

    
    public void clear() {
        ConcurrentBag.Node<Sink<? super T>> head = mSinks.head();
        
        while( head != null ) {
            head.mItem.clear();
            head = head.mNext;
        }
    }
    
    
    public void close() throws IOException {
        mClosed = true;
        
        IOException err = null;
        ConcurrentBag.Node<Sink<? super T>> head = mSinks.head();
        mSinks.clear();
        
        while( head != null ) {
            try {
                head.mItem.close();
            } catch( IOException ex ) {
                err = IOExceptionList.join( err, ex ); 
            }
            
            head = head.mNext;
        }
        
        if( err != null ) {
            throw err;
        }
    } 

    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    public void addSink( Sink<? super T> sink ) {
        if( mClosed ) {
            return;
        }
        mSinks.add( sink );
    }
    
    
    public boolean removeSink( Sink<? super T> sink ) {
        if( mClosed ) {
            return false;
        }
        return mSinks.remove( sink );
    }

    
    public boolean containsSink() {
        return !mSinks.isEmpty();
    }
    
   
    public boolean containsSink( Object obj ) {
        return mSinks.contains( obj );
    }

    
    public boolean containsSinkOtherThan( Object obj ) {
        ConcurrentBag.Node<Sink<? super T>> node = mSinks.head();
        if( node == null ) {
            return false;
        }
        
        if( node.mNext != null ) {
            return true;
        }
        
        return obj != node.mItem && ( obj == null || !obj.equals( node.mItem ) );
    }
    
}
