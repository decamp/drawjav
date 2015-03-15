/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import java.io.*;

import bits.drawjav.*;



/**
 * @author decamp
 */
@Deprecated public class AudioResamplerPipe implements Sink<DrawPacket> {

    private final Sink<? super DrawPacket> mSink;
    private final AudioResampler           mResampler;


    public AudioResamplerPipe( Sink<? super DrawPacket> sink,
                               StreamFormat destFormat,
                               AudioAllocator alloc )
    {
        mSink = sink;
        mResampler = new AudioResampler( alloc );
        mResampler.requestFormat( destFormat );
    }


    public StreamFormat destFormat() {
        return mResampler.destFormat();
    }
    
    
    public void consume( DrawPacket packet ) throws IOException {
        DrawPacket p = mResampler.convert( packet );
        if( p != null ) {
            mSink.consume( p );
            p.deref();
        }
    }
    
    
    public void clear() {
        mResampler.clear();
        mSink.clear();
    }
    
    
    public void close() throws IOException {
        mResampler.close();
        mSink.close();
    }
    
    
    public boolean isOpen() {
        return true;
    }
    
}
