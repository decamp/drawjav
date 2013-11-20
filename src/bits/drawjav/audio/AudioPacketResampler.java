package bits.drawjav.audio;

import java.io.*;
import java.nio.*;

import bits.drawjav.*;
import bits.jav.Jav;



/**
 * TODO: Pretty crappy. Doesn't actually handle multiple format types.
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
    
    private ByteBuffer mSrcBuf = null;
    private float[] mSrcArr  = null;
    private float[] mDstArr = null;
    
    
    public AudioPacketResampler( AudioFormat sourceFormat,
                                 AudioFormat destFormat,
                                 int poolSize ) 
    {
        assert( sourceFormat.sampleFormat() == Jav.AV_SAMPLE_FMT_FLT );
        
        mSourceFormat = sourceFormat;
        mDestFormat   = new AudioFormat( sourceFormat.channels(), 
                                         destFormat.sampleRate() <= 0 ? sourceFormat.sampleRate() : destFormat.sampleRate(),
                                         Jav.AV_SAMPLE_FMT_FLT );
        
        if( mDestFormat.sampleRate() == mSourceFormat.sampleRate() ) {
            mFactory   = null;
            mResampler = null;
        } else {
            mFactory   = new AudioPacketFactory( poolSize );
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
        
        int chans  = mSourceFormat.channels();
        int frames = packet.nbSamples();
        int samps  = frames * chans;
        
        ByteBuffer srcBuf = mSrcBuf;
        float[] srcArr = mSrcArr;
        if( srcArr == null || srcArr.length < samps ) {
            mSrcBuf = srcBuf = ByteBuffer.allocateDirect( samps * 2 * 4 / 3 ).order( ByteOrder.nativeOrder() );
            mSrcArr = srcArr = new float[samps * 4 / 3];
        }
        
        srcBuf.clear().limit( samps * 2 );
        for( int i = 0; i < chans; i++ ) {
            packet.readData( i, srcBuf );
        }
        srcBuf.flip();
        
        float scale = -1f / Short.MIN_VALUE;
        
        for( int i = 0; i < frames; i++ ) {
            for( int j = 0; j < chans; j++ ) {
                srcArr[ i*chans+j ] = srcBuf.getShort( i+j*frames ) * scale; 
            }
        }
        
        int dstLen = mResampler.recommendOutBufferSize( samps );
        float[] dstArr = mDstArr;
        if( dstArr == null || dstArr.length < dstLen ) {
            dstArr = new float[dstLen * 4 / 3];
            mDstArr = dstArr;
        }
        
        dstLen = mResampler.processSamples( srcArr, 0, dstArr, 0, frames );
        if( dstLen <= 0 ) {
            return null;
        }
        
        AudioPacket dst = mFactory.build( packet.stream(),
                                          packet.startMicros(),
                                          packet.stopMicros(),
                                          mDestFormat,
                                          dstLen / mDestFormat.channels() );
        
        FloatBuffer dstBuf = dst.directBuffer().asFloatBuffer();
        dstBuf.put( dstArr, 0, dstLen );
        dst.nbSamples( dstLen );
        
        if( mInitMicros ) {
            mInitMicros  = false;
            mStartMicros = packet.startMicros();
            mSampleCount = 0;
        }
        
        long freq        = mDestFormat.sampleRate();
        chans            = mDestFormat.channels();
        long startMicros = mStartMicros + mSampleCount * 1000000L / ( chans * freq );
        mSampleCount += dstLen;
        long stopMicros  = mStartMicros + mSampleCount * 1000000L / ( chans * freq );
        
        // Update mStartMicros and mSampleCount at integer boundaries to avoid huge multiplies.
        mStartMicros += ( mSampleCount / ( chans * freq ) ) * 1000000L;
        mSampleCount %= ( chans * freq );
        
        //Create and send new packet.
        dst.init( packet.stream(), mDestFormat, startMicros, stopMicros );
        
        System.out.println( dst.channels() + "\t" + dst.nbSamples() );
        
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
