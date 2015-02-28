    /*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.nio.*;

import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import bits.jav.JavException;
import bits.jav.codec.*;
import bits.jav.util.Rational;
import bits.util.ref.*;


/**
 * @author decamp
 */
public class VideoPacket extends JavFrame implements Packet {

    private static final Rational ONE = new Rational( 1, 1 );

    
    public static VideoPacket createAuto( ObjectPool<? super VideoPacket> optPool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError( "Allocation failed." );
        }
        return new VideoPacket( p, optPool );
    }
    
    
    public static VideoPacket createFilled( ObjectPool<? super VideoPacket> optPool,
                                            PictureFormat format )
                                            throws JavException
    {
        int size = nComputeVideoBufferSize( format.width(), format.height(), format.pixelFormat() );
        ByteBuffer buf = ByteBuffer.allocateDirect( size );
        buf.order( ByteOrder.nativeOrder() );
        return createFilled( optPool, format, buf );
    }
    
    
    public static VideoPacket createFilled( ObjectPool<? super VideoPacket> optPool,
                                            PictureFormat format,
                                            ByteBuffer buf )
                                            throws JavException
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }
        VideoPacket ret = new VideoPacket( pointer, optPool );
        ret.fillVideoFrame( format.width(), format.height(), format.pixelFormat(), buf );
        ret.mFormat = format;
        Rational rat = format.sampleAspect();
        if( rat != null ) {
            ret.sampleAspectRatio( rat );
        }
        return ret;
    }


    private StreamHandle  mStream;
    private PictureFormat mFormat;
    private long          mStartMicros;
    private long          mStopMicros;
    private boolean       mIsGap;


    @SuppressWarnings( { "unchecked", "rawtypes" } )
    private VideoPacket( long pointer, ObjectPool<? super VideoPacket> pool )
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


    public void setFormat( PictureFormat format ) {
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
            setFormat( optFormat );
        }
    }

}
