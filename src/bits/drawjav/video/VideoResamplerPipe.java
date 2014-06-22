package bits.drawjav.video;

import java.io.IOException;

import bits.drawjav.*;


/**
 * Decodes a single stream.
 * 
 * @author decamp
 */
public class VideoResamplerPipe implements Sink<VideoPacket> {


    private final Sink<? super VideoPacket> mSink;
    private final VideoPacketResampler mConverter;

    public VideoResamplerPipe( Sink<? super VideoPacket> sink,
                               PictureFormat sourceFormat,
                               VideoAllocator alloc )
    {
        mSink  = sink;
        mConverter = new VideoPacketResampler( alloc );
        mConverter.setSourceFormat( sourceFormat );
    }
    
    
    
    public void setPictureConversion( PictureFormat format, int swsFlags ) {
        mConverter.setDestFormat( format );
        mConverter.setConversionFlags( swsFlags );
    }

    
    public PictureFormat destFormat() {
        return mConverter.getDestFormat();
    }
    
    
    public void consume( VideoPacket packet ) throws IOException {
        VideoPacket ret = null;
        try {
            ret = mConverter.process( packet );
            mSink.consume( ret );
        } finally {
            if( ret != null ) {
                ret.deref();
            }
        }
    }
    
    
    public void clear() {
        mSink.clear();
    }
    

    public void close() throws IOException {
        mConverter.close();
        mSink.close();
    }

    
    public boolean isOpen() {
        return true;
    }
    
}
