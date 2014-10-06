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
        //mSink.consume( packet );
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
