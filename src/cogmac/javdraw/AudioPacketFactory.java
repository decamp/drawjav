package cogmac.javdraw;

import java.io.Closeable;
import java.nio.FloatBuffer;
import java.util.*;

import cogmac.langx.ref.RefPool;


/**
 * @author decamp
 */
public class AudioPacketFactory implements RefPool<AudioPacket>, Closeable {

    
    private final AudioFormat mFormat;
    private final List<AudioPacket> mQueue = new ArrayList<AudioPacket>();
    private int mCapacity;
    
    
    public AudioPacketFactory( AudioFormat format, int poolSize ) {
        mFormat   = format;
        mCapacity = poolSize;
    }
    
    
        
    public synchronized AudioPacket build( int capacity, StreamHandle stream, long startMicros, long stopMicros ) {
        AudioPacket ret = poll();
        if( ret == null || ret.bufferRef().capacity() < capacity ) {
            ret = new AudioPacket( this, FloatBuffer.allocate(capacity + 16) );
        }
        
        ret.init( stream, mFormat, startMicros, stopMicros );
        ret.bufferRef().clear();
        return ret;
    }
    
    
    public synchronized boolean offer( AudioPacket obj ) {
        if( mCapacity >= 0 && mQueue.size() >= mCapacity ) {
            return false;
        }
            
        mQueue.add( obj );
        return true;
    }

    
    public synchronized AudioPacket poll() {
        int n = mQueue.size();
        if( n == 0 ) {
            return null;
        }
        AudioPacket p = mQueue.remove( n - 1 );
        p.ref();
        return p;
    }
    
    
    public synchronized void close() {
        if( mCapacity == 0 ) {
            return;
        }
        mCapacity = 0;
        mQueue.clear();
    }
    

}
