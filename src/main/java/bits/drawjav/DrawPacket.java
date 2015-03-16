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
                                          StreamFormat format,
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
            format.getAudioProperties( ret );
            return ret;
        }

        ByteBuffer buf = Jav.allocEncodingBuffer( size );
        return createAudio( optPool, format, samplesPerChannel, align, buf );
    }


    public static DrawPacket createAudio( ObjectPool<? super DrawPacket> optPool,
                                          StreamFormat format,
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

//    private static final List<Exception> ALL_CREATED_ATS = new ArrayList<Exception>();
//    private int mCreatedAt;


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    protected DrawPacket( long pointer, ObjectPool<? super DrawPacket> pool ) {
        super( pointer, (ObjectPool)pool );
//        synchronized( DrawPacket.class ) {
//            Exception e = new Exception();
//            e.fillInStackTrace();
//            mCreatedAt = ALL_CREATED_ATS.size();
//            ALL_CREATED_ATS.add( e );
//        }
    }


    @Override
    public Stream stream() {
        return mStream;
    }


    public void stream( Stream stream ) {
        mStream = stream;
    }


    public int type() {
        return mStream == null ? Jav.AVMEDIA_TYPE_UNKNOWN : mStream.format().mType;
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

    /**
     * Initializes packet object.
     */
    public void init( StreamFormat optFormat,
                      long startMicros,
                      long stopMicros,
                      boolean isGap )
    {
        mStartMicros = startMicros;
        mStopMicros = stopMicros;
        mIsGap = isGap;

        if( optFormat != null ) {
            optFormat.getProperties( this );
        }
    }

    @Override
    protected void finalize() throws Throwable {
        long p = pointer();
        if( p != 0L ) {
            synchronized( DrawPacket.class ) {
                if( !sHasWarnedAboutFinalization ) {
                    sHasWarnedAboutFinalization = true;
                    LOG.warning( "Frame finalized without being destroyed." );
                    //Exception e = ALL_CREATED_ATS.get( mCreatedAt );
                    //e.printStackTrace();
                }
            }
        }
        super.finalize();
    }

}
