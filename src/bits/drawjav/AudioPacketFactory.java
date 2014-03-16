package bits.drawjav;

import java.io.Closeable;
import java.util.*;

import bits.jav.codec.JavFrame;
import bits.util.ref.ObjectPool;



/**
 * @author decamp
 */
public class AudioPacketFactory implements ObjectPool<AudioPacket>, Closeable {

    
    private final List<AudioPacket> mQueue;
    private int mCapacity;
    
    private final AudioFormat mFormat;
    private final int mNumSamples;
    
    public AudioPacketFactory( int poolSize ) {
        this( poolSize, null, -1 );
    }
    
    
    public AudioPacketFactory( int poolSize, AudioFormat format, int numSamples ) {
        mCapacity   = Math.max( 1, poolSize );
        mQueue      = new ArrayList<AudioPacket>( mCapacity );
        
        if( format == null || numSamples <= 0 ) {
            mFormat = null;
            mNumSamples = -1;
        } else {
            mFormat = format;
            mNumSamples = numSamples;
        }
    }
    
    
    
    public synchronized AudioPacket build( StreamHandle stream, 
                                           long startMicros, 
                                           long stopMicros ) 
    {
        return build( stream, startMicros, stopMicros, mFormat, mNumSamples );
    }
    
    
    public synchronized AudioPacket build( StreamHandle stream,
                                           long startMicros,
                                           long stopMicros,
                                           AudioFormat format,
                                           int numSamples )
    {
        AudioPacket ret = poll();
        
        if( format != null && numSamples > 0 ) {
            if( ret == null ) {
                ret = AudioPacket.newFormattedInstance( this, format, numSamples, 0 );
            } else {
                int cap = JavFrame.computeAudioBufferSize( format.channels(), numSamples, format.sampleFormat(), 0, null );
                if( ret.directBufferCapacity() < cap ) {
                    // Don't deref frame and return it pool. Just let it be garbage-collected and create new frame.
                    ret = AudioPacket.newFormattedInstance( this, format, numSamples, 0 );
                } else {
                    ret.nbSamples( numSamples );
                }
            }
        } else if( ret == null ) {
            ret = AudioPacket.newAutoInstance( this );
        }
        
        ret.init( stream, format, startMicros, stopMicros );
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
