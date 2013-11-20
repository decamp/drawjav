package bits.drawjav;

import java.nio.*;

import bits.jav.codec.JavFrame;
import bits.langx.ref.*;



/**
 * @author decamp
 */
public class AudioPacket extends JavFrame implements Packet {
    
    
    public static AudioPacket newAutoInstance() {
        return newAutoInstance( null );
    }
    
    
    public static AudioPacket newAutoInstance( RefPool<? super AudioPacket> pool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError("Allocation failed.");
        }
        return new AudioPacket( p, pool );
    }
    
    
    public static AudioPacket newFormattedInstance( RefPool<? super AudioPacket> pool,
                                                    AudioFormat format,
                                                    int samplesPerChannel,
                                                    int align ) 
    {
        int size = nComputeAudioBufferSize( format.channels(),
                                            samplesPerChannel,
                                            format.sampleFormat(),
                                            align,
                                            null );
        ByteBuffer buf = ByteBuffer.allocateDirect( size );
        buf.order( ByteOrder.nativeOrder() );
        return newFormattedInstance( pool, format, samplesPerChannel, align, buf );
    }
    
    
    public static AudioPacket newFormattedInstance( RefPool<? super AudioPacket> pool, 
                                                    AudioFormat format,
                                                    int samplesPerChannel,
                                                    int align,
                                                    ByteBuffer buf )
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }
        AudioPacket ret = new AudioPacket( pointer, pool );
        ret.fillAudioFrame( format.channels(), 
                            samplesPerChannel, 
                            format.sampleFormat(),
                            buf,
                            buf.position(),
                            align );
        ret.audioFormat( format );
        return ret;
    }

    
    
    private StreamHandle mStream;
    private long mStartMicros;
    private long mStopMicros;
    private AudioFormat mFormat;
    
    
    @SuppressWarnings( { "unchecked", "rawtypes" } )
    public AudioPacket( long pointer, 
                        RefPool<? super AudioPacket> pool )
    {
        super( pointer, (RefPool)pool );
    }
    
    
    
    public StreamHandle stream() {
        return mStream;
    }
    
    
    public long startMicros() {
        return mStartMicros;
    }

    
    public long stopMicros() {
        return mStopMicros;
    }

    
    public AudioFormat audioFormat() {
        return mFormat;
    }
    
    /**
     * Associates frame with a different audio format object.
     * 
     * @param audioFormat
     */
    public void audioFormat( AudioFormat audioFormat ) {
        mFormat = audioFormat;
        format( audioFormat.sampleFormat() );
    }
    
    /**
     * Initializes packet object.
     * 
     * @param frame
     * @param format
     * @param startMicros
     * @param stopMicros
     */
    public void init( StreamHandle stream,
                      AudioFormat format, 
                      long startMicros, 
                      long stopMicros) 
    {
        mStream      = stream;
        mStartMicros = startMicros;
        mStopMicros  = stopMicros;
        audioFormat( format );
    }

    
}
