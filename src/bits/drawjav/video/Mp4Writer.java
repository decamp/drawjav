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
    
    
    private int mWidth   = 0;
    private int mHeight  = 0;
    
    private Rational mTimeBase = new Rational( 1001, 30000 );
    private int mBitRate = 400000;
    private int mQuality = 20;
    private boolean mEncMode = CONSTANT_QUALITY;
    private int mGopSize = 24;
    
    private JavDict mMetadata = null;
    
    private JavFormatContext mFormat     = null;
    private JavCodecContext  mCc         = null;
    private JavPacket        mPacket     = JavPacket.alloc();
    private JavFrame         mSrcFrame   = JavFrame.alloc();
    private JavFrame         mDstFrame   = null;
    private SwsContext       mSws        = null;
    private Rational         mStreamRate = null;
    private final int[]      mBufOffsets = { 0 };
    private final int[]      mLineSizes  = { 0 };
    private final int[]      mGotPacket  = { 0 };
    
    
    public Mp4Writer() {}
    
    
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
                mCc.bitRate( mBitRate );
            } else {
                JavOption.setOptionDouble( priv, "crf", mQuality, 0 );
            }
            JavOption.setOptionString( priv, "preset", "slow", 0 );
        }
        
        mCc.open( codec );
        if( mMetadata != null ) {
            mFormat.metadata( mMetadata );
            mMetadata = null;
        }
         
        mFormat.writeHeader();
        mStreamRate = stream.timeBase();
        
        mDstFrame = JavFrame.allocVideo( mWidth, mHeight, Jav.AV_PIX_FMT_YUV420P, null );
        mSws = SwsContext.allocAndInit( mWidth, mHeight, Jav.AV_PIX_FMT_RGB24, 
                                        mWidth, mHeight, Jav.AV_PIX_FMT_YUV420P, 
                                        Jav.SWS_POINT );
    }
    
    
    public void write( ByteBuffer buf, int bytesPerRow ) throws IOException {
        mLineSizes[0] = bytesPerRow;
        mSrcFrame.fillVideoFrameManually( mWidth, 
                                          mHeight, 
                                          Jav.AV_PIX_FMT_RGB24,
                                          1,
                                          buf,
                                          mBufOffsets,
                                          mLineSizes );
        mSws.convert( mSrcFrame, 0, mHeight, mDstFrame );
        
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
