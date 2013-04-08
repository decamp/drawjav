package cogmac.javdraw.audio;

import java.io.*;
import java.nio.FloatBuffer;

import cogmac.javdraw.*;


/**
 * @author decamp
 */
public class AudioPacketResampler implements PacketConverter<AudioPacket> {
    
    private final AudioFormat mSourceFormat;
    private final AudioFormat mDestFormat;
    
    private final AudioPacketFactory mFactory;
    private final AudioResampler mResampler;
    
    private boolean mInitMicros  = true;
    private long mStartMicros    = 0;
    private long mSampleCount    = 0;
    
    
    public AudioPacketResampler( AudioFormat sourceFormat,
                                 AudioFormat destFormat,
                                 int poolSize ) 
    {
        mSourceFormat = sourceFormat;
        mDestFormat   = new AudioFormat( sourceFormat.channels(), 
                                         destFormat.sampleRate() <= 0 ? sourceFormat.sampleRate() : destFormat.sampleRate(), 
                                         sourceFormat.sampleFormat() );
        
        if( mDestFormat.sampleRate() == mSourceFormat.sampleRate() ) {
            mFactory   = null;
            mResampler = null;
        } else {
            mFactory   = new AudioPacketFactory( destFormat, poolSize );
            mResampler = new AudioResampler( sourceFormat.sampleRate(),
                                             destFormat.sampleRate(),
                                             sourceFormat.channels() );
        }
    }
    
    
    
    public AudioFormat sourceFormat() {
        return mSourceFormat;
    }
    
    
    public AudioFormat destFormat() {
        return mDestFormat;
    }
    
    
    public AudioPacket convert( AudioPacket packet ) throws IOException {
        if( mResampler == null ) {
            packet.ref();
            return packet;
        }
        
        FloatBuffer srcBuf = packet.buffer();
        int srcLen         = srcBuf.remaining();
        int dstLen         = mResampler.recommendOutBufferSize( srcLen );
        AudioPacket dst    = mFactory.build( dstLen, null, 0, 0 );
        FloatBuffer dstBuf = dst.bufferRef();
        
        dstLen = mResampler.processSamples( srcBuf.array(), srcBuf.position(), dstBuf.array(), 0, srcLen );
        
        if( dstLen <= 0 ) {
            dst.deref();
            return null;
        }
        
        dstBuf.limit( dstLen );
        
        if( mInitMicros ) {
            mInitMicros  = false;
            mStartMicros = packet.getStartMicros();
            mSampleCount = 0;
        }
        
        long freq        = mDestFormat.sampleRate();
        long chans       = mDestFormat.channels();
        long startMicros = mStartMicros + mSampleCount * 1000000L / ( chans * freq );
        mSampleCount += dstLen;
        long stopMicros  = mStartMicros + mSampleCount * 1000000L / ( chans * freq );
        
        // Update mStartMicros and mSampleCount at integer boundaries to avoid huge multiplies.
        mStartMicros += ( mSampleCount / ( chans * freq ) ) * 1000000L;
        mSampleCount %= ( chans * freq );
        
        //Create and send new packet.
        dst.init( packet.stream(), mDestFormat, startMicros, stopMicros );
        return dst;
    }

    
    public AudioPacket drain() throws IOException {
        // TODO: Implement this.
        return null;
    }
    
    
    public void clear() {
        if( mResampler != null ) {
            mResampler.clear();
        }
        mInitMicros = true;
    }
    
    
    public void close() {
        if( mFactory != null ) {
            mFactory.close();
        }
        if( mResampler != null ) {
            mResampler.clear();
        }
    }
    
}
