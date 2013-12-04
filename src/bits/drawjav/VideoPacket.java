package bits.drawjav;

import java.nio.*;

import bits.jav.JavException;
import bits.jav.codec.*;
import bits.langx.ref.*;


/**
 * @author decamp
 */
public class VideoPacket extends JavFrame implements Packet {
    
    
    public static VideoPacket newAutoInstance( RefPool<? super VideoPacket> pool ) {
        long p = nAllocFrame();
        if( p == 0 ) {
            throw new OutOfMemoryError( "Allocation failed." );
        }
        return new VideoPacket( p, pool );
    }
    
    
    public static VideoPacket newFormattedInstance( RefPool<? super VideoPacket> pool,
                                                    PictureFormat format )
                                                    throws JavException
    {
        int size = nComputeVideoBufferSize( format.width(), format.height(), format.pixelFormat() );
        ByteBuffer buf = ByteBuffer.allocateDirect( size );
        buf.order( ByteOrder.nativeOrder() );
        return newFormattedInstance( pool, format, buf );
    }
    
    
    public static VideoPacket newFormattedInstance( RefPool<? super VideoPacket> pool, 
                                                    PictureFormat format,
                                                    ByteBuffer buf )
                                                    throws JavException
    {
        long pointer = nAllocFrame();
        if( pointer == 0 ) {
            throw new OutOfMemoryError();
        }
        VideoPacket ret = new VideoPacket( pointer, pool );
        ret.fillVideoFrame( format.width(), format.height(), format.pixelFormat(), buf );
        ret.pictureFormat( format );
        return ret;
    }

    
    
    private StreamHandle mStream;
    private long mStartMicros;
    private long mStopMicros;
    
    private PictureFormat mPictureFormat; 
    
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private VideoPacket( long pointer, RefPool<? super VideoPacket> pool ) {
        super( pointer, (RefPool)pool );
    }

    
    
    public StreamHandle stream() {
        return mStream;
    }
    

    @Override
    public long startMicros() {
        return mStartMicros;
    }


    @Override
    public long stopMicros() {
        return mStopMicros;
    }

    
    public PictureFormat pictureFormat() {
        return mPictureFormat;
    }
    
    /**
     * Associates frame with a different picture format object,
     * but DOES NOT make any changes to underlying data.
     * 
     * @param pictureFormat
     */
    public void pictureFormat( PictureFormat pictureFormat ) {
        mPictureFormat = pictureFormat;
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
                      PictureFormat format, 
                      long startMicros, 
                      long stopMicros) 
    {
        mStream      = stream;
        mStartMicros = startMicros;
        mStopMicros  = stopMicros;
        
        pictureFormat(format);
    }

}