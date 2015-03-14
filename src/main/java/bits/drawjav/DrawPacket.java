/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.nio.*;
import java.util.logging.Logger;

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
    private static final Logger   LOG = Logger.getLogger( DrawPacket.class.getName() );

    private static boolean sHasWarnedAboutFinalization = false;


    public static DrawPacket createAuto( ObjectPool<? super DrawPacket> optPool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError( "Allocation failed." );
        }
        return new DrawPacket( p, optPool );
    }


    public static DrawPacket createVideo( ObjectPool<? super DrawPacket> optPool,
                                          StreamFormat format )
    {
        int size = nComputeVideoBufferSize( format.mWidth, format.mHeight, format.mPixelFormat );
        size += Jav.FF_INPUT_BUFFER_PADDING_SIZE;
        ByteBuffer buf = Jav.allocEncodingBuffer( size );
        return createVideo( optPool, format, buf );
    }
    
    
    public static DrawPacket createVideo( ObjectPool<? super DrawPacket> optPool,
                                          StreamFormat format,
                                          ByteBuffer buf )
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }
        DrawPacket ret = new DrawPacket( pointer, optPool );
        try {
            ret.fillVideoFrame( format.mWidth, format.mHeight, format.mPixelFormat, buf );
        } catch( JavException e ) {
            throw new RuntimeException( e );
        }

        Rational rat = format.mSampleAspect;
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

        int size = JavSampleFormat.getBufferSize( format.mChannels,
                                                  samplesPerChannel,
                                                  format.mSampleFormat,
                                                  align,
                                                  null );

        if( size < 0 ) {
            DrawPacket ret = createAuto( optPool );
            ret.setAudioFormat( format );
            return ret;
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
        ret.fillAudioFrame( format.mChannels,
                            samplesPerChannel,
                            format.mSampleFormat,
                            buf,
                            align );
        return ret;
    }


    private Stream  mStream;
    private long    mStartMicros;
    private long    mStopMicros;
    private boolean mIsGap;


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    protected DrawPacket( long pointer, ObjectPool<? super DrawPacket> pool )
    {
        super( pointer, (ObjectPool)pool );
    }


    @Override
    public Stream stream() {
        return mStream;
    }


    public void stream( Stream stream ) {
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
        mStopMicros = stopMicros;
    }


    public boolean isGap() {
        return mIsGap;
    }


    public void isGap( boolean gap ) {
        mIsGap = gap;
    }


    public void setPictureFormat( StreamFormat format ) {
        format( format.mPixelFormat );
        width( format.mWidth );
        height( format.mHeight );
        sampleAspectRatio( format.mSampleAspect );
    }


    @Deprecated public StreamFormat toPictureFormat() {
        return StreamFormat.createVideo( this );
    }


    public AudioFormat toAudioFormat() {
        return new AudioFormat( channels(), sampleRate(), format(), channelLayout() );
    }


    public void setAudioFormat( AudioFormat format ) {
        sampleRate( format.mSampleRate );
        channels( format.mChannels );
        format( format.mSampleFormat );
        channelLayout( format.mChannelLayout );
    }


    /**
     * Initializes packet object. 
     *
     * @param optStream
     * @param optFormat
     * @param startMicros
     * @param stopMicros
     */
    public void init( Stream optStream,
                      long startMicros,
                      long stopMicros,
                      StreamFormat optFormat,
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
    public void init( Stream stream,
                      long startMicros,
                      long stopMicros,
                      AudioFormat format,
                      boolean isGap )
    {
        mStream = stream;
        mStartMicros = startMicros;
        mStopMicros = stopMicros;
        mIsGap = isGap;
        if( format != null ) {
            setAudioFormat( format );
        }
    }


    @Override
    protected void finalize() throws Throwable {
        long p = pointer();
        if( p != 0L ) {
            synchronized( DrawPacket.class ) {
                if( !sHasWarnedAboutFinalization ) {
                    sHasWarnedAboutFinalization = false;
                    LOG.warning( "Frame finalized without being destroyed." );
                }
            }
        }
        super.finalize();
    }

}
