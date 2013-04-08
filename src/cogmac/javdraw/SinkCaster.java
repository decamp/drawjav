package cogmac.javdraw;

import java.io.IOException;

/**
 * Threadsafe structure for sending method calls to multiple sinks.
 * 
 * @author decamp
 */
public class SinkCaster<T> implements Sink<T> {
    
    
    public static <T> Sink<? super T> add( Sink<? super T> a, Sink<? super T> b ) {
        if( a == null ) return b;
        if( b == null ) return a;
        return new SinkCaster<T>( a, b );
    }
    
    
    public static <T> Sink<? super T> remove( Sink<? super T> s, Sink<?> old ) {
        if( s == null ) {
            return null;
        }
        if( old != null && ( s instanceof SinkCaster ) ) {
            return ((SinkCaster<? super T>)s).remove( old );
        }
        return s;
    }
    
    
    
    protected final Sink<? super T> mA;
    protected final Sink<? super T> mB;
    
    
    protected SinkCaster( Sink<? super T> a, Sink<? super T> b ) {
        mA = a;
        mB = b;
    }
    
    
    
    public void consume( T frame ) throws IOException {
        IOException err = null;
        
        try {
            mA.consume( frame );
        } catch( IOException ex ) {
            err = ex;
        }
        
        try {
            mB.consume( frame );
        }catch( IOException ex ) {
            err = ex;
        }
        
        if(err != null) {
            throw err;
        }
    }

    
    public void clear() {
        mA.clear();
        mB.clear();
    }
    
    
    public void close() throws IOException {
        IOException t = null;
        
        try {
            mA.close();
        } catch( IOException ex ) {
            t = ex;
        }
        
        try {
            mB.close();
        } catch( IOException ex ) {
            t = ex;
        }
        
        if( t != null ) {
            throw t;
        }
    } 

    

    protected Sink<? super T> remove( Sink<?> old ) {
        if( old == mA ) {
            return mB;
        }
        if( old == mB ) {
            return mA;
        }
        
        Sink<? super T> a2;
        Sink<? super T> b2;
        if( mA instanceof SinkCaster ) {
            a2 = ((SinkCaster<? super T>)mA).remove( old );
        } else {
            a2 = mA;
        }
        if( mB instanceof SinkCaster ) {
            b2 = ((SinkCaster<? super T>)mB).remove( old );
        } else {
            b2 = mB;
        }
        
        if( a2 == mA && b2 == mB ) {
            // Not here.
            return this;
        }
        
        return add( a2, b2 );
    }
    

}
