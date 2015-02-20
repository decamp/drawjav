/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.nio.*;

import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import bits.jav.JavException;
import bits.jav.codec.JavFrame;
import bits.jav.util.JavSampleFormat;
import bits.util.ref.*;



/**
 * @author decamp
 */
public class AudioPacket extends JavFrame implements Packet {
    

    public static AudioPacket createAuto( ObjectPool<? super AudioPacket> optPool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError("Allocation failed.");
        }
        return new AudioPacket( p, optPool );
    }
    
    
    public static AudioPacket createFilled( ObjectPool<? super AudioPacket> optPool,
                                            AudioFormat format,
                                            int samplesPerChannel,
                                            int align )
    {
        assert samplesPerChannel >= 0;

        int size = JavSampleFormat.getBufferSize( format.channels(),
                                                  samplesPerChannel,
                                                  format.sampleFormat(),
                                                  align,
                                                  null );

        if( size < 0 ) {
            throw new RuntimeException( new JavException( size ) );
        }

        ByteBuffer buf = ByteBuffer.allocateDirect( size );
        buf.order( ByteOrder.nativeOrder() );
        return createFilled( optPool, format, samplesPerChannel, align, buf );
    }
    
    
    public static AudioPacket createFilled( ObjectPool<? super AudioPacket> optPool,
                                            AudioFormat format,
                                            int samplesPerChannel,
                                            int align,
                                            ByteBuffer buf )
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }

        AudioPacket ret = new AudioPacket( pointer, optPool );
        ret.fillAudioFrame( format.channels(),
                            samplesPerChannel,
                            format.sampleFormat(),
                            buf,
                            align );

        ret.audioFormat( format );
        return ret;
    }


    private StreamHandle mStream;
    private long         mStartMicros;
    private long         mStopMicros;
    private AudioFormat  mFormat;


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public AudioPacket( long pointer, ObjectPool<? super AudioPacket> optPool ) {
        super( pointer, (ObjectPool)optPool );
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
     */
    public void audioFormat( AudioFormat audioFormat ) {
        mFormat = audioFormat;
        format( audioFormat.sampleFormat() );
    }

    /**
     * Initializes packet object.
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
