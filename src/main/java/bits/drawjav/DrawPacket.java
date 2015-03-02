/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.nio.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.jav.Jav;
import bits.jav.JavException;
import bits.jav.codec.*;
import bits.jav.util.JavSampleFormat;
import bits.jav.util.Rational;
import bits.util.ref.*;


/**
 * The basic packet class for the drawjav library, with minor functionality added to JavFrame.
 *
 * @author decamp
 */
public class DrawPacket extends JavFrame implements Packet {

    private static final Rational ONE = new Rational( 1, 1 );

    
    public static DrawPacket createAuto( ObjectPool<? super DrawPacket> optPool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError( "Allocation failed." );
        }
        return new DrawPacket( p, optPool );
    }
    
    
    public static DrawPacket createVideo( ObjectPool<? super DrawPacket> optPool,
                                          PictureFormat format )
                                          throws JavException
    {
        int size = nComputeVideoBufferSize( format.width(), format.height(), format.pixelFormat() );
        size += Jav.FF_INPUT_BUFFER_PADDING_SIZE;
        ByteBuffer buf = Jav.allocEncodingBuffer( size );
        return createVideo( optPool, format, buf );
    }
    
    
    public static DrawPacket createVideo( ObjectPool<? super DrawPacket> optPool,
                                          PictureFormat format,
                                          ByteBuffer buf )
                                          throws JavException
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }
        DrawPacket ret = new DrawPacket( pointer, optPool );
        ret.fillVideoFrame( format.width(), format.height(), format.pixelFormat(), buf );
        Rational rat = format.sampleAspect();
        if( rat != null ) {
            ret.sampleAspectRatio( rat );
        }
        return ret;
    }


    public static DrawPacket createAudio( ObjectPool<? super DrawPacket> optPool,
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

        ByteBuffer buf = Jav.allocEncodingBuffer( size );
        return createAudio( optPool, format, samplesPerChannel, align, buf );
    }


    public static DrawPacket createAudio( ObjectPool<? super DrawPacket> optPool,
                                          AudioFormat format,
                                          int samplesPerChannel,
                                          int align,
                                          ByteBuffer buf )
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }

        DrawPacket ret = new DrawPacket( pointer, optPool );
        ret.fillAudioFrame( format.channels(),
                            samplesPerChannel,
                            format.sampleFormat(),
                            buf,
                            align );
        return ret;
    }



    private StreamHandle  mStream;
    private long          mStartMicros;
    private long          mStopMicros;
    private boolean       mIsGap;


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    protected DrawPacket( long pointer, ObjectPool<? super DrawPacket> pool )
    {
        super( pointer, (ObjectPool)pool );
    }


    @Override
    public StreamHandle stream() {
        return mStream;
    }


    public void stream( StreamHandle stream ) {
        mStream = stream;
    }


    public int type() {
        return mStream == null ? Jav.AVMEDIA_TYPE_UNKNOWN : mStream.type();
    }

    @Override
    public long startMicros() {
        return mStartMicros;
    }


    public void startMicros( long startMicros ) {
        mStartMicros = startMicros;
    }

    @Override
    public long stopMicros() {
        return mStopMicros;
    }


    public void stopMicros( long stopMicros ) {
        mStopMicros  = stopMicros;
    }


    public boolean isGap() {
        return mIsGap;
    }


    public void isGap( boolean gap ) {
        mIsGap = gap;
    }


    public void setPictureFormat( PictureFormat format ) {
        format( format.pixelFormat() );
        width( format.width() );
        height( format.height() );
        sampleAspectRatio( format.sampleAspect() );
    }


    public PictureFormat toPictureFormat() {
        Rational aspect = sampleAspectRatio();
        if( aspect.num() == 0 ) {
            aspect = null;
        }
        return new PictureFormat( width(), height(), format(), aspect );
    }


    public AudioFormat toAudioFormat() {
        return new AudioFormat( channels(), sampleRate(), format(), channelLayout() );
    }


    public void setAudioFormat( AudioFormat format ) {
        sampleRate( format.sampleRate() );
        channels( format.channels() );
        format( format.sampleFormat() );
        channelLayout( format.channelLayout() );
    }



    /**
     * Initializes packet object. 
     *
     * @param optStream
     * @param optFormat
     * @param startMicros
     * @param stopMicros
     */
    public void init( StreamHandle optStream,
                      long startMicros,
                      long stopMicros,
                      PictureFormat optFormat,
                      boolean isGap )
    {
        mStream = optStream;
        mStartMicros = startMicros;
        mStopMicros = stopMicros;
        mIsGap = isGap;
        if( optFormat != null ) {
            setPictureFormat( optFormat );
        }
    }


    /**
     * Initializes packet object.
     */
    public void init( StreamHandle stream,
                      long startMicros,
                      long stopMicros,
                      AudioFormat format,
                      boolean isGap )
    {
        mStream      = stream;
        mStartMicros = startMicros;
        mStopMicros  = stopMicros;
        mIsGap       = isGap;
        if( format != null ) {
            setAudioFormat( format );
        }
    }

}
