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

    private static final float SAMP_SCALE = -1f / Short.MIN_VALUE;

    private final AudioFormat mSourceFormat;
    private final AudioFormat mDestFormat;

    private final AudioAllocator mAlloc;
    private final AudioResampler mResampler;

    private boolean mInitMicros  = true;
    private long    mStartMicros = 0;
    private long    mFrameCount  = 0;

    private ByteBuffer mSrcBuf = null;
    private float[]    mSrcArr = null;
    private float[]    mDstArr = null;

    private boolean mClosed = false;


    public AudioPacketResampler( AudioFormat sourceFormat,
                                 AudioFormat destFormat,
                                 AudioAllocator alloc )
    {
        assert sourceFormat.sampleFormat() == Jav.AV_SAMPLE_FMT_FLT;
        mAlloc        = alloc;
        mSourceFormat = sourceFormat;
        mDestFormat   = new AudioFormat( sourceFormat.channels(), 
                                         destFormat.sampleRate() <= 0 ? sourceFormat.sampleRate() : destFormat.sampleRate(),
                                         Jav.AV_SAMPLE_FMT_FLT );

        if( mDestFormat.sampleRate() == mSourceFormat.sampleRate() ) {
            mResampler = null;
        } else {
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
        final int chans     = mSourceFormat.channels();
        final int srcFrames = packet.nbSamples();
        final int srcVals   = srcFrames * chans;

        if( mSrcArr == null || mSrcArr.length < srcVals ) {
            int allocLen = srcVals * 4 / 3;
            mSrcBuf = ByteBuffer.allocateDirect( 2 * allocLen / chans ).order( ByteOrder.nativeOrder() );
            mSrcArr = new float[allocLen];
        }
        final ByteBuffer srcBuf = mSrcBuf;
        final float[] srcArr    = mSrcArr;

        srcBuf.clear();
        for( int i = 0; i < chans; i++ ) {
            srcBuf.clear().limit( 2 * srcFrames );
            packet.readData( i, srcBuf );
            srcBuf.flip();

            for( int j = 0; j < srcFrames; j++ ) {
                srcArr[ j*chans + i ] = SAMP_SCALE * srcBuf.getShort();
            }
        }

        float[] dstArr;
        int dstFrames;

        if( mResampler == null ) {
            dstFrames = srcFrames;
            dstArr    = srcArr;

        } else {
            int dstCap = mResampler.recommendOutBufferSize( srcFrames );
            dstArr = mDstArr;
            if( dstArr == null || dstArr.length < dstCap ) {
                dstArr = new float[dstCap];
                mDstArr = dstArr;
            }

            dstFrames = mResampler.process( srcArr, 0, dstArr, 0, srcFrames );
            if( dstFrames <= 0 ) {
                return null;
            }
        }

        AudioPacket dst = mAlloc.alloc( mDestFormat, dstFrames );
        ByteBuffer dstBuf = dst.directBuffer();
        dstBuf.order( ByteOrder.nativeOrder() );
        int dstVals = dstFrames * chans;

        for( int i = 0; i < dstVals; i++ ) {
            dstBuf.putFloat( dstArr[i] );
        }
        dstBuf.flip();
        dst.nbSamples( dstFrames );

        if( mInitMicros ) {
            mInitMicros  = false;
            mStartMicros = packet.startMicros();
            mFrameCount = 0;
        }
        
        long freq        = mDestFormat.sampleRate();
        long startMicros = mStartMicros + mFrameCount * 1000000L / freq;
        mFrameCount += dstFrames;
        long stopMicros  = mStartMicros + mFrameCount * 1000000L / freq;
        
        // Update mStartMicros and mFrameCount at integer boundaries to avoid huge multiplies.
        mStartMicros += ( mFrameCount / freq ) * 1000000L;
        mFrameCount %= freq;
        
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
        if( mClosed ) {
            return;
        }
        mClosed = true;

        if( mAlloc != null ) {
            mAlloc.deref();
        }
        if( mResampler != null ) {
            mResampler.clear();
        }
    }
    
}
