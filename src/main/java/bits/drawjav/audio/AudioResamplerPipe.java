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
public class AudioResamplerPipe implements Sink<AudioPacket> {
    
    private final Sink<? super AudioPacket> mSink;
    private final AudioPacketResampler mResampler;
    
    
    public AudioResamplerPipe( Sink<? super AudioPacket> sink,
                               AudioFormat destFormat,
                               AudioAllocator alloc )
    {
        mSink      = sink;
        mResampler = new AudioPacketResampler( alloc );
        mResampler.destFormat( destFormat );
    }



    public AudioFormat destFormat() {
        return mResampler.destFormat();
    }
    
    
    public void consume( AudioPacket packet ) throws IOException {
        AudioPacket p = mResampler.convert( packet );
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
