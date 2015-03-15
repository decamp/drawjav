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
public class VideoResamplerPipe implements Sink<DrawPacket> {


    private final Sink<? super DrawPacket> mSink;
    private final VideoResampler           mConverter;

    public VideoResamplerPipe( Sink<? super DrawPacket> sink,
                               StreamFormat sourceFormat,
                               VideoAllocator alloc )
    {
        mSink = sink;
        mConverter = new VideoResampler( alloc );
        mConverter.sourceFormat( sourceFormat );
    }


    public void setPictureConversion( StreamFormat format, int swsFlags ) {
        mConverter.requestFormat( format );
        mConverter.conversionFlags( swsFlags );
    }

    
    public StreamFormat destFormat() {
        return mConverter.destFormat();
    }
    
    
    public void consume( DrawPacket packet ) throws IOException {
        DrawPacket ret = null;
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
