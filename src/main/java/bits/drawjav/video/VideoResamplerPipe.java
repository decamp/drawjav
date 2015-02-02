/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

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
        mConverter.sourceFormat( sourceFormat );
    }
    
    
    
    public void setPictureConversion( PictureFormat format, int swsFlags ) {
        mConverter.destFormat( format );
        mConverter.conversionFlags( swsFlags );
    }

    
    public PictureFormat destFormat() {
        return mConverter.destFormat();
    }
    
    
    public void consume( VideoPacket packet ) throws IOException {
        VideoPacket ret = null;
        try {
            ret = mConverter.convert( packet );
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
