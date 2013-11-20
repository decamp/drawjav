package bits.drawjav.video;

import java.io.IOException;

import bits.drawjav.*;
import bits.jav.swscale.SwsFilter;



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
                               int poolSize ) 
    {
        mSink      = sink;
        mConverter = new VideoPacketResampler( poolSize );
        mConverter.setSourceFormat( sourceFormat );
    }
    
    
    
    public void setPictureConversion( PictureFormat format, 
                                      int swsFlags,
                                      SwsFilter sourceFilter,
                                      SwsFilter destFilter,
                                      int cropTop,
                                      int cropBottom )
    {
        mConverter.setDestFormat(format);
        mConverter.setConversionFlags(swsFlags);
        mConverter.setSourceFilter(sourceFilter);
        mConverter.setDestFilter(destFilter);
        mConverter.setCrop(cropTop, cropBottom);
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
