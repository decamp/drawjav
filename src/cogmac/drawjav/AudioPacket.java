package cogmac.drawjav;

import java.nio.FloatBuffer;

import cogmac.jav.JavConstants;
import cogmac.langx.ref.*;


/**
 * @author decamp
 */
public class AudioPacket extends AbstractRefable implements Packet {
    
    
    private final FloatBuffer  mBuffer;
    
    private StreamHandle mStream;
    private AudioFormat mFormat;
    
    private long mStartMicros;
    private long mStopMicros;
    
    
    public AudioPacket( ObjectPool<? super AudioPacket> pool,
                        FloatBuffer buffer )
    {
        super(pool);
        mBuffer = buffer;
    }
    
                        
    
    
    public StreamHandle stream() {
        return mStream;
    }
    
    
    public int type() {
        return JavConstants.AVMEDIA_TYPE_AUDIO;
    }

    
    public long getStartMicros() {
        return mStartMicros;
    }

    
    public long getStopMicros() {
        return mStopMicros;
    }

    
    public FloatBuffer buffer() {
        return mBuffer.duplicate();
    }
    
    
    public FloatBuffer bufferRef() {
        return mBuffer;
    }
    
    
    public AudioFormat format() {
        return mFormat;
    }

    
    public int sampleCount() {
        return mBuffer.remaining();
    }
    
    
    
    
    protected void freeObject() {}
    
    
    
    public void init( StreamHandle stream,
                      AudioFormat format,
                      long startMicros, 
                      long stopMicros) 
    {
        mStream      = stream;
        mFormat      = format;
        mStartMicros = startMicros;
        mStopMicros  = stopMicros;
    }
    
}
