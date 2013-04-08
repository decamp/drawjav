package cogmac.javdraw;

import java.io.IOException;


public class CasterPipe<T> implements Sink<T> {

    private Sink<? super T> mCaster = null;
    private boolean mClosed = false;
    
    
    public CasterPipe() {} 
    
    
    @Deprecated
    public boolean isEmpty() {
        return mCaster == null;
    }
    
    
    public synchronized void addSink( Sink<? super T> sink ) {
        if( mClosed ) {
            return;
        }
        mCaster = SinkCaster.add( mCaster, sink );
    }


    public synchronized boolean removeSink( Sink<? super T> sink ) {
        if( mClosed ) {
            return false;
        }
        Sink<? super T> caster = mCaster;
        mCaster = SinkCaster.remove( caster, sink );
        return mCaster != caster;
    }

    
    public boolean hasSink() {
        return mCaster != null;
    }
    
    
    public synchronized boolean hasSinkOtherThan( Sink<? super T> sink ) {
        return mCaster != null && mCaster != sink;
    }
    
    
    public void consume( T packet ) throws IOException {
        Sink<? super T> caster = mCaster;
        if( caster != null ) {
            caster.consume( packet );
        }
    }

    
    public void clear() {
        Sink<? super T> caster = mCaster;
        if( caster != null ) {
            caster.clear();
        }
    }
    

    public void close() throws IOException {
        Sink<? super T> caster;
        
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            caster = mCaster;
            mCaster = null;
        }
        
        if( caster != null ) {
            caster.close();
        }
    }

}
