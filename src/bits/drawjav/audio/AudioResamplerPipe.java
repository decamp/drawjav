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
                               AudioFormat sourceFormat,
                               AudioFormat destFormat,
                               AudioAllocator alloc )
    {
        mSink      = sink;
        mResampler = new AudioPacketResampler( sourceFormat, destFormat, alloc );
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
