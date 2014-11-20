/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.io.*;
import java.nio.ByteBuffer;

import bits.jav.Jav;
import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.swscale.SwsContext;
import bits.jav.util.*;


/**
 * @author decamp
 */
public class Mp4Writer {

    private static final boolean CONSTANT_BIT_RATE = false;
    private static final boolean CONSTANT_QUALITY  = true;
    
    
    private int mWidth      = 0;
    private int mHeight     = 0;
    private int mFrameCount = 0;
    
    private Rational mTimeBase = new Rational( 1001, 30000 );
    private int      mBitRate  = 400000;
    private int      mQuality  = 20;
    private boolean  mEncMode  = CONSTANT_QUALITY;
    private int      mGopSize  = 24;
    
    private JavFormatContext mFormat     = null;
    private JavCodecContext  mCc         = null;
    private JavPacket        mPacket;
    private JavFrame         mSrcFrame;
    private JavFrame         mDstFrame   = null;
    private SwsContext       mSws        = null;
    private Rational         mStreamRate = null;
    private JavDict          mMetadata   = null;
    private final int[]      mGotPacket  = { 0 };
    
    
    public Mp4Writer() {
        Jav.init();
        mPacket    = JavPacket.alloc();
        mSrcFrame  = JavFrame.alloc();
        mSrcFrame.format( Jav.AV_PIX_FMT_BGR24 );
        assert mSrcFrame.extendedData() == mSrcFrame.data();
    }
    
    
    public Mp4Writer size( int w, int h ) {
        mWidth  = w;
        mHeight = h;
        return this;
    }
    
    /**
     * @param timeBase  Timebase of encoding. Default is 30000/1001.
     * @return this
     */
    public Mp4Writer timeBase( Rational timeBase ) {
        mTimeBase = timeBase;
        return this;
    }
    
    /**
     * @param bitRate Bits per second for output encoding. Sets Mp4Writer to use constant bitrate. <br/>
     *                Default is constant quality mode.
     * @return this
     */
    public Mp4Writer bitrate( int bitRate ) {
        mBitRate = bitRate;
        mEncMode = CONSTANT_BIT_RATE;
        return this;
    }

    /**
     * @param quality Number between 0 (highest quality) and 100 (lowest quality). <br/> 
     *                Sets Mp4Writer to use encode using constant quality (Variable Bit Rate). <br/>
     *                Default is 20.
     * @return this                
     */
    public Mp4Writer quality( int quality ) {
        mQuality = quality;
        return this;
    }

    /**
     * @param gopSize  Number of frames in group before new keyframe. Default is 24.
     * @return this
     */
    public Mp4Writer gopSize( int gopSize ) {
        mGopSize = gopSize;
        return this;
    }
    
    
    public Mp4Writer metadata( JavDict meta ) {
        mMetadata = meta;
        return this;
    }

    
    
    public void open( File out ) throws IOException {
        close();
        
        mFormat = JavFormatContext.openOutput( out, null, "mp4" );
        JavOutputFormat outFmt = mFormat.outputFormat();
        JavCodec codec = JavCodec.findEncoder( Jav.AV_CODEC_ID_H264 );
        JavStream stream = mFormat.newStream( codec );
        mCc = stream.codecContext();
        mCc.width( mWidth );
        mCc.height( mHeight );
        mCc.timeBase( mTimeBase );
        mCc.gopSize( mGopSize );
        mCc.maxBFrames( 0 );
        mCc.pixelFormat( Jav.AV_PIX_FMT_YUV420P );

        if( ( outFmt.flags() & Jav.AVFMT_GLOBALHEADER ) != 0 ) {
            mCc.flags( mCc.flags() | Jav.CODEC_FLAG_GLOBAL_HEADER );
        }
        
        {   // Codec specific settings.
            JavClass priv = mCc.privData();
            if( mEncMode == CONSTANT_BIT_RATE ) {
                mCc.qmin( 20 );
                mCc.qmax( 60 );
                mCc.bitRate( mBitRate );
                mCc.rcMaxRate( mBitRate * 3 / 2 );
                mCc.rcBufferSize( mBitRate * 3 ) ;
                JavOption.setString( priv, "preset", "slow", 0 );
            } else {
                JavOption.setDouble( priv, "crf", mQuality, 0 );
                JavOption.setString( priv, "preset", "slow", 0 );
            }

        }
        
        mCc.open( codec );
        if( mMetadata != null ) {
            mFormat.metadata( mMetadata );
            mMetadata = null;
        }
         
        mFormat.writeHeader();
        mStreamRate = stream.timeBase();
        
        mDstFrame = JavFrame.allocVideo( mWidth, mHeight, Jav.AV_PIX_FMT_YUV420P, null );
        mSws = SwsContext.allocAndInit( mWidth, mHeight, Jav.AV_PIX_FMT_BGR24, 
                                        mWidth, mHeight, Jav.AV_PIX_FMT_YUV420P, 
                                        Jav.SWS_POINT );
    }
    
    
    public void write( ByteBuffer buf, int bytesPerRow ) throws IOException {
        mSrcFrame.width( mWidth );
        mSrcFrame.height( mHeight );
        mSrcFrame.lineSize( 0, bytesPerRow );
        long ptr = JavMem.nativeAddress( buf ) + buf.position();
        mSrcFrame.dataElem( 0, ptr );

        mSws.conv( mSrcFrame, mDstFrame );
        long pts = Rational.rescaleQ( mFrameCount++, mTimeBase, mStreamRate );
        mDstFrame.pts( pts );
        
        mPacket.init();
        mPacket.freeData();
        int err = mCc.encodeVideo( mDstFrame, mPacket, mGotPacket ); 
        if( err < 0 ) {
            throw new IOException( "Encode failed: " + err );
        }
        if( mGotPacket[0] == 0 ) {
            return;
        }

        err = mFormat.writeFrame( mPacket );
        if( err < 0 ) {
            throw new IOException( "Packet write failed: " + err );
        }
    }
    
    
    public void close() throws IOException {
        if( mFormat == null ) {
            return;
        }
        
        while( true ) {
            mPacket.init();
            mPacket.freeData();
            int err = mCc.encodeVideo( null, mPacket, mGotPacket );
            if( err < 0 ) {
                throw new IOException( "Encode failed: " + err );
            }
            if( mGotPacket[0] == 0 ) {
                break;
            }
            err = mFormat.writeFrame( mPacket );
            if( err < 0 ) {
                throw new IOException( "Packet write failed: " + err );
            }
        }
        
        mFormat.writeFrame( null );
        mFormat.writeTrailer();
        mFormat.close();

        mFormat = null;
        mCc     = null;
        mPacket.init();
        mPacket.freeData();

        if( mDstFrame != null ) {
            mDstFrame.deref();
            mDstFrame = null;
        }
        
        if( mSws != null ) {
            mSws.release();
            mSws = null;
        }
        
    }

    
}
