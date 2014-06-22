package bits.drawjav.audio;

import java.nio.*;

import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.util.ref.*;



/**
 * @author decamp
 */
public class AudioPacket extends JavFrame implements Packet {
    
    
    public static AudioPacket createAuto() {
        return createAuto( null );
    }
    
    
    public static AudioPacket createAuto( ObjectPool<? super AudioPacket> pool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError("Allocation failed.");
        }
        return new AudioPacket( p, pool );
    }
    
    
    public static AudioPacket createFilled( ObjectPool<? super AudioPacket> pool,
                                            AudioFormat format,
                                            int samplesPerChannel,
                                            int align )
    {
        assert samplesPerChannel >= 0;

        int size = nComputeAudioBufferSize( format.channels(),
                                            samplesPerChannel,
                                            format.sampleFormat(),
                                            align,
                                            null );

        if( size < 0 ) {
            throw new RuntimeException( new JavException( size ) );
        }

        ByteBuffer buf = ByteBuffer.allocateDirect( size );
        buf.order( ByteOrder.nativeOrder() );
        return createFilled( pool, format, samplesPerChannel, align, buf );
    }
    
    
    public static AudioPacket createFilled( ObjectPool<? super AudioPacket> pool,
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
    private long         mStartMicros;
    private long         mStopMicros;
    private AudioFormat  mFormat;


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public AudioPacket( long pointer, ObjectPool<? super AudioPacket> pool ) {
        super( pointer, (ObjectPool)pool );
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
     * @param stream
     * @param format
     * @param startMicros
     * @param stopMicros
     */
    public void init( StreamHandle stream,
                      AudioFormat format,
                      long startMicros,
                      long stopMicros )
    {
        mStream = stream;
        mStartMicros = startMicros;
        mStopMicros = stopMicros;
        audioFormat( format );
    }

}
